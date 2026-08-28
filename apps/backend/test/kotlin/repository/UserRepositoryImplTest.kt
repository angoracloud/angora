package cloud.angora.repository

import cloud.angora.Users
import cloud.angora.constants.BackendConstants
import cloud.angora.testsupport.AuthFixtures
import cloud.angora.testsupport.PostgresRepositoryTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UserRepositoryImplTest : PostgresRepositoryTest() {

    private val repository = UserRepositoryImpl(database)

    @AfterEach
    fun cleanup() {
        AuthFixtures.cleanupAuthTables(database)
    }

    private fun seedUser(
        email: String = "admin@example.com",
        status: String = BackendConstants.UserStatus.ACTIVE,
        deletedAt: Instant? = null,
        lockedUntil: Instant? = null,
        failedLoginAttempts: Short = 0,
        roleName: String = BackendConstants.RoleNames.OWNER
    ): UUID {
        val companyId = AuthFixtures.createCompany(database)
        return AuthFixtures.createUser(
            database = database,
            companyId = companyId,
            email = email,
            status = status,
            deletedAt = deletedAt,
            lockedUntil = lockedUntil,
            failedLoginAttempts = failedLoginAttempts,
            roleName = roleName
        )
    }

    @Test
    fun `findByEmail returns the user with their role name`() {
        val userId = seedUser(roleName = BackendConstants.RoleNames.ADMIN)

        val found = repository.findByEmail("admin@example.com")

        assertNotNull(found)
        assertEquals(userId, found?.id)
        assertEquals(BackendConstants.RoleNames.ADMIN, found?.roleName)
        assertEquals("not-a-real-hash", found?.passwordHash)
    }

    @Test
    fun `findByEmail is case insensitive`() {
        seedUser(email = "Admin@Example.com")

        // Login must not depend on the casing the user happens to type, and the
        // lookup has to hit the lower(email) index from V7.
        assertNotNull(repository.findByEmail("admin@example.com"))
        assertNotNull(repository.findByEmail("ADMIN@EXAMPLE.COM"))
        assertNotNull(repository.findByEmail("Admin@Example.com"))
    }

    @Test
    fun `findByEmail ignores soft-deleted users`() {
        seedUser(deletedAt = Instant.now())

        assertNull(repository.findByEmail("admin@example.com"))
    }

    @Test
    fun `findByEmail returns non-active users so the caller can decide`() {
        // The repository must not filter on status: the service needs the record
        // in order to reject it with the same generic error as everything else.
        seedUser(status = "suspended")

        assertEquals("suspended", repository.findByEmail("admin@example.com")?.status)
    }

    @Test
    fun `findById returns the user and skips soft-deleted ones`() {
        val userId = seedUser()
        assertEquals(userId, repository.findById(userId)?.id)

        val deletedId = seedUser(email = "gone@example.com", deletedAt = Instant.now())
        assertNull(repository.findById(deletedId))
    }

    @Test
    fun `recordFailedLogin increments without locking below the threshold`() {
        val userId = seedUser()
        val lockUntil = Instant.now().plusSeconds(900)

        repository.recordFailedLogin(userId, lockThreshold = 5, lockedUntil = lockUntil)

        val row = readUser(userId)
        assertEquals(1.toShort(), row.first)
        assertNull(row.second)
    }

    @Test
    fun `recordFailedLogin locks the account once the threshold is reached`() {
        val userId = seedUser(failedLoginAttempts = 4)
        val lockUntil = Instant.now().plusSeconds(900)

        repository.recordFailedLogin(userId, lockThreshold = 5, lockedUntil = lockUntil)

        val row = readUser(userId)
        assertEquals(5.toShort(), row.first)
        assertNotNull(row.second)
    }

    @Test
    fun `recordFailedLogin does not lose concurrent increments`() {
        val userId = seedUser()
        val lockUntil = Instant.now().plusSeconds(900)
        val threads = 8

        // Without a row lock, simultaneous failures read the same count and write
        // the same count+1, so an attacker guessing in parallel could stay under
        // the lockout threshold forever.
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
        val ready = java.util.concurrent.CountDownLatch(threads)
        val go = java.util.concurrent.CountDownLatch(1)
        try {
            val futures = (1..threads).map {
                pool.submit {
                    ready.countDown()
                    go.await()
                    repository.recordFailedLogin(userId, lockThreshold = 100, lockedUntil = lockUntil)
                }
            }
            ready.await()
            go.countDown()
            futures.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        assertEquals(threads.toShort(), readUser(userId).first)
    }

    @Test
    fun `recordSuccessfulLogin clears the counter and the lock`() {
        val userId = seedUser(
            failedLoginAttempts = 4,
            lockedUntil = Instant.now().plusSeconds(900)
        )

        repository.recordSuccessfulLogin(userId, Instant.now())

        val row = readUser(userId)
        assertEquals(0.toShort(), row.first)
        assertNull(row.second)
        assertNotNull(transaction(database) {
            Users.selectAll().where { Users.id eq userId }.first()[Users.lastLoginAt]
        })
    }

    /** Returns (failedLoginAttempts, lockedUntil) straight from the row. */
    private fun readUser(userId: UUID): Pair<Short, Instant?> = transaction(database) {
        val row = Users.selectAll().where { Users.id eq userId }.first()
        row[Users.failedLoginAttempts] to row[Users.lockedUntil]
    }
}
