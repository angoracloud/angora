package cloud.angora.service

import cloud.angora.constants.BackendConstants
import cloud.angora.error.ApiException
import cloud.angora.repository.ActiveSession
import cloud.angora.repository.AuthUserRecord
import cloud.angora.repository.SessionRepository
import cloud.angora.repository.UserRepository
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
// The Assertions.assertThrows overload, not the reified `assertThrows<T>`: that
// one is an inline function compiled for JVM target 17, which won't inline into
// this module's 1.8 target.
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private const val CORRECT_PASSWORD = "correct-password"
private const val STORED_HASH = "stored-hash"

private class FakeUserRepository(private var user: AuthUserRecord?) : UserRepository {
    var failedLoginCalls = 0
    var successfulLoginCalls = 0
    var lastLockThreshold: Int? = null

    override fun findByEmail(email: String): AuthUserRecord? =
        user?.takeIf { it.email.equals(email, ignoreCase = true) }

    override fun findById(userId: UUID): AuthUserRecord? = user?.takeIf { it.id == userId }

    override fun recordFailedLogin(userId: UUID, lockThreshold: Int, lockedUntil: Instant) {
        failedLoginCalls++
        lastLockThreshold = lockThreshold
    }

    override fun recordSuccessfulLogin(userId: UUID, at: Instant) {
        successfulLoginCalls++
    }
}

private class FakeSessionRepository : SessionRepository {
    var created: Pair<UUID, String>? = null
    var revokedSession: UUID? = null
    var revokedAllForUser: UUID? = null
    var active: ActiveSession? = null
    var touched: UUID? = null

    override fun create(
        userId: UUID,
        tokenHash: String,
        expiresAt: Instant,
        ipAddress: String?,
        userAgent: String?
    ): UUID {
        created = userId to tokenHash
        return UUID.randomUUID()
    }

    override fun findActiveByTokenHash(tokenHash: String): ActiveSession? = active

    override fun revoke(sessionId: UUID, at: Instant) {
        revokedSession = sessionId
    }

    override fun revokeAllForUser(userId: UUID, at: Instant): Int {
        revokedAllForUser = userId
        return 0
    }

    override fun touch(sessionId: UUID, at: Instant) {
        touched = sessionId
    }

    override fun deleteExpired(now: Instant): Int = 0
}

/** Treats [CORRECT_PASSWORD] against [STORED_HASH] as the only valid pairing. */
private class FakePasswordService : PasswordService {
    var dummyVerifyCalls = 0

    override fun hash(plaintext: String): String = STORED_HASH
    override fun verify(plaintext: String, storedHash: String): Boolean =
        plaintext == CORRECT_PASSWORD && storedHash == STORED_HASH

    override fun dummyVerify() {
        dummyVerifyCalls++
    }
}

private class FakeTokenService : TokenService {
    override fun generate(): String = "generated-token"
    override fun hash(token: String): String = "hashed:$token"
}

class AuthServiceImplTest {

    private fun userRecord(
        status: String = BackendConstants.UserStatus.ACTIVE,
        lockedUntil: Instant? = null
    ) = AuthUserRecord(
        id = UUID.randomUUID(),
        companyId = UUID.randomUUID(),
        email = "admin@example.com",
        fullName = "Admin User",
        passwordHash = STORED_HASH,
        status = status,
        roleName = BackendConstants.RoleNames.OWNER,
        lockedUntil = lockedUntil
    )

    private fun serviceFor(
        users: FakeUserRepository,
        sessions: FakeSessionRepository = FakeSessionRepository(),
        passwords: FakePasswordService = FakePasswordService()
    ) = AuthServiceImpl(users, sessions, passwords, FakeTokenService())

    @Test
    fun `issues a session on a correct password`() {
        val user = userRecord()
        val users = FakeUserRepository(user)
        val sessions = FakeSessionRepository()

        val result = serviceFor(users, sessions)
            .login(user.email, CORRECT_PASSWORD, "10.0.0.1", "curl/8")

        assertEquals("generated-token", result.token)
        assertEquals(user.id.toString(), result.user.id)
        assertEquals(BackendConstants.RoleNames.OWNER, result.user.role)
        // The raw token must never be what lands in the database.
        assertEquals(user.id to "hashed:generated-token", sessions.created)
        assertEquals(1, users.successfulLoginCalls)
    }

    @Test
    fun `every rejection reason produces the same error`() {
        // Each case is (label, repository, email, password). The status and lock
        // cases deliberately use the *correct* password: rejecting them is only
        // meaningful if the credentials would otherwise have worked.
        val cases = listOf(
            Case("unknown email", FakeUserRepository(null), "nobody@example.com", CORRECT_PASSWORD),
            Case("wrong password", FakeUserRepository(userRecord()), "admin@example.com", "wrong-password"),
            Case(
                "locked account",
                FakeUserRepository(userRecord(lockedUntil = Instant.now().plusSeconds(600))),
                "admin@example.com",
                CORRECT_PASSWORD
            ),
            Case("suspended", FakeUserRepository(userRecord(status = "suspended")), "admin@example.com", CORRECT_PASSWORD),
            Case("invited", FakeUserRepository(userRecord(status = "invited")), "admin@example.com", CORRECT_PASSWORD),
            Case("deactivated", FakeUserRepository(userRecord(status = "deactivated")), "admin@example.com", CORRECT_PASSWORD)
        )

        val expected = Triple(
            HttpStatusCode.Unauthorized,
            BackendConstants.Errors.INVALID_CREDENTIALS_CODE,
            BackendConstants.Errors.INVALID_CREDENTIALS_MESSAGE
        )

        cases.forEach { case ->
            val error = assertThrows(ApiException::class.java) {
                serviceFor(case.users).login(case.email, case.password, null, null)
            }
            assertEquals(
                expected,
                Triple(error.statusCode, error.code, error.message),
                "reason '${case.label}' leaked a distinguishable response"
            )
        }
    }

    private data class Case(
        val label: String,
        val users: FakeUserRepository,
        val email: String,
        val password: String
    )

    @Test
    fun `spends time hashing even when the email is unknown`() {
        val passwords = FakePasswordService()
        val service = serviceFor(FakeUserRepository(null), passwords = passwords)

        assertThrows(ApiException::class.java) { service.login("nobody@example.com", "irrelevant", null, null) }

        // Without this, a missing account returns measurably faster than a wrong
        // password and the endpoint becomes a user-enumeration oracle.
        assertEquals(1, passwords.dummyVerifyCalls)
    }

    @Test
    fun `counts a failed attempt against the lockout threshold`() {
        val users = FakeUserRepository(userRecord())

        assertThrows(ApiException::class.java) {
            serviceFor(users).login("admin@example.com", "wrong-password", null, null)
        }

        assertEquals(1, users.failedLoginCalls)
        assertEquals(BackendConstants.Auth.MAX_FAILED_LOGIN_ATTEMPTS, users.lastLockThreshold)
    }

    @Test
    fun `does not count an attempt against a locked account`() {
        val users = FakeUserRepository(userRecord(lockedUntil = Instant.now().plusSeconds(600)))

        assertThrows(ApiException::class.java) {
            serviceFor(users).login("admin@example.com", CORRECT_PASSWORD, null, null)
        }

        // Already locked; re-counting would let an attacker extend the lockout
        // indefinitely and lock a user out on purpose.
        assertEquals(0, users.failedLoginCalls)
    }

    @Test
    fun `lets a user back in once the lock has expired`() {
        val users = FakeUserRepository(userRecord(lockedUntil = Instant.now().minusSeconds(1)))

        val result = serviceFor(users).login("admin@example.com", CORRECT_PASSWORD, null, null)

        assertNotNull(result.token)
    }

    @Test
    fun `resolves a principal from an active session and marks it seen`() {
        val sessions = FakeSessionRepository()
        val sessionId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        sessions.active = ActiveSession(sessionId, userId, companyId, BackendConstants.RoleNames.ADMIN)

        val principal = serviceFor(FakeUserRepository(null), sessions).resolvePrincipal("tok")

        assertEquals(userId, principal?.userId)
        assertEquals(companyId, principal?.companyId)
        assertEquals(BackendConstants.RoleNames.ADMIN, principal?.roleName)
        assertEquals(sessionId, sessions.touched)
    }

    @Test
    fun `resolves to nothing when the session is not usable`() {
        val sessions = FakeSessionRepository()

        assertNull(serviceFor(FakeUserRepository(null), sessions).resolvePrincipal("tok"))
        // An unusable session must not be marked seen either.
        assertNull(sessions.touched)
    }

    @Test
    fun `logout and logoutAll revoke different scopes`() {
        // The one bug worth guarding here is mixing the two up: a plain logout
        // that ends every session, or a "log out everywhere" that ends only one.
        val single = FakeSessionRepository()
        val sessionId = UUID.randomUUID()
        serviceFor(FakeUserRepository(null), single).logout(sessionId)

        assertEquals(sessionId, single.revokedSession)
        assertNull(single.revokedAllForUser)

        val all = FakeSessionRepository()
        val userId = UUID.randomUUID()
        serviceFor(FakeUserRepository(null), all).logoutAll(userId)

        assertEquals(userId, all.revokedAllForUser)
        assertNull(all.revokedSession)
    }

    @Test
    fun `never exposes credential material in the user response`() {
        val user = userRecord()
        val result = serviceFor(FakeUserRepository(user))
            .login(user.email, CORRECT_PASSWORD, null, null)

        val rendered = result.user.toString()
        assertFalse(rendered.contains(STORED_HASH))
        assertTrue(rendered.contains(user.email))
    }
}
