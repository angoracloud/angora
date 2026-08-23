package cloud.angora.repository

import cloud.angora.Sessions
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SessionRepositoryImplTest : PostgresRepositoryTest() {

    private val repository = SessionRepositoryImpl(database)

    @AfterEach
    fun cleanup() {
        AuthFixtures.cleanupAuthTables(database)
    }

    private fun seedUser(
        email: String = "admin@example.com",
        status: String = BackendConstants.UserStatus.ACTIVE,
        deletedAt: Instant? = null,
        roleName: String = BackendConstants.RoleNames.MEMBER
    ): UUID {
        val companyId = AuthFixtures.createCompany(database)
        return AuthFixtures.createUser(
            database = database,
            companyId = companyId,
            email = email,
            status = status,
            deletedAt = deletedAt,
            roleName = roleName
        )
    }

    private fun createSession(
        userId: UUID,
        tokenHash: String = "hash-${UUID.randomUUID()}",
        expiresAt: Instant = Instant.now().plusSeconds(3600)
    ): UUID = repository.create(
        userId = userId,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        ipAddress = "10.0.0.1",
        userAgent = "curl/8"
    )

    @Test
    fun `resolves an active session to its user, company and role`() {
        val userId = seedUser(roleName = BackendConstants.RoleNames.ADMIN)
        val sessionId = createSession(userId, tokenHash = "token-hash")

        val found = repository.findActiveByTokenHash("token-hash")

        assertNotNull(found)
        assertEquals(sessionId, found?.sessionId)
        assertEquals(userId, found?.userId)
        assertEquals(BackendConstants.RoleNames.ADMIN, found?.roleName)
    }

    @Test
    fun `does not resolve an unknown token`() {
        seedUser().also { createSession(it) }

        assertNull(repository.findActiveByTokenHash("no-such-hash"))
    }

    @Test
    fun `does not resolve an expired session`() {
        val userId = seedUser()
        createSession(userId, tokenHash = "expired", expiresAt = Instant.now().minusSeconds(1))

        assertNull(repository.findActiveByTokenHash("expired"))
    }

    @Test
    fun `does not resolve a revoked session`() {
        val userId = seedUser()
        val sessionId = createSession(userId, tokenHash = "revoked")

        repository.revoke(sessionId, Instant.now())

        assertNull(repository.findActiveByTokenHash("revoked"))
    }

    @Test
    fun `does not resolve a session whose user was suspended`() {
        // The whole point of server-side sessions: suspending a user has to end
        // the sessions they already hold, not just block new logins.
        val userId = seedUser(status = "suspended")
        createSession(userId, tokenHash = "suspended-user")

        assertNull(repository.findActiveByTokenHash("suspended-user"))
    }

    @Test
    fun `does not resolve a session whose user was soft-deleted`() {
        val userId = seedUser(deletedAt = Instant.now())
        createSession(userId, tokenHash = "deleted-user")

        assertNull(repository.findActiveByTokenHash("deleted-user"))
    }

    @Test
    fun `revokeAllForUser ends every live session for that user only`() {
        val userId = seedUser()
        val otherUserId = seedUser(email = "other@example.com")
        createSession(userId, tokenHash = "a")
        createSession(userId, tokenHash = "b")
        createSession(otherUserId, tokenHash = "c")

        val revoked = repository.revokeAllForUser(userId, Instant.now())

        assertEquals(2, revoked)
        assertNull(repository.findActiveByTokenHash("a"))
        assertNull(repository.findActiveByTokenHash("b"))
        assertNotNull(repository.findActiveByTokenHash("c"))
    }

    @Test
    fun `revoking twice keeps the first revocation time`() {
        val userId = seedUser()
        val sessionId = createSession(userId, tokenHash = "once")
        val firstRevoke = Instant.now()

        repository.revoke(sessionId, firstRevoke)
        val recorded = readSession(sessionId).revokedAt
        repository.revoke(sessionId, firstRevoke.plusSeconds(600))

        // The update is guarded on revoked_at IS NULL, so a second logout can't
        // move the timestamp and rewrite when the session actually ended.
        assertEquals(recorded, readSession(sessionId).revokedAt)
        assertNull(repository.findActiveByTokenHash("once"))
    }

    @Test
    fun `touch records when the session was last seen`() {
        val userId = seedUser()
        val sessionId = createSession(userId, tokenHash = "touched")
        assertNull(readSession(sessionId).lastSeenAt)

        val first = Instant.now()
        repository.touch(sessionId, first)
        val afterFirst = readSession(sessionId).lastSeenAt
        assertNotNull(afterFirst)

        repository.touch(sessionId, first.plusSeconds(60))

        assertTrue(readSession(sessionId).lastSeenAt!!.isAfter(afterFirst))
    }

    /** The raw timestamp columns, which the repository's own return types don't expose. */
    private fun readSession(sessionId: UUID): SessionRow = transaction(database) {
        val row = Sessions.selectAll().where { Sessions.id eq sessionId }.first()
        SessionRow(row[Sessions.revokedAt], row[Sessions.lastSeenAt])
    }

    private data class SessionRow(val revokedAt: Instant?, val lastSeenAt: Instant?)

    @Test
    fun `deleteExpired removes only sessions already past their expiry`() {
        val userId = seedUser()
        createSession(userId, tokenHash = "live", expiresAt = Instant.now().plusSeconds(3600))
        createSession(userId, tokenHash = "stale", expiresAt = Instant.now().minusSeconds(3600))

        val deleted = repository.deleteExpired(Instant.now())

        assertEquals(1, deleted)
        assertNotNull(repository.findActiveByTokenHash("live"))
    }
}
