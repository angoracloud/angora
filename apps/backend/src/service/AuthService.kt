package cloud.angora.service

import cloud.angora.auth.UserPrincipal
import cloud.angora.constants.BackendConstants
import cloud.angora.dto.AuthUserResponse
import cloud.angora.error.ApiException
import cloud.angora.repository.AuthUserRecord
import cloud.angora.repository.SessionRepository
import cloud.angora.repository.UserRepository
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/** A successful login: the raw token to hand back as a cookie, plus the user to render. */
data class LoginResult(
    val token: String,
    val user: AuthUserResponse
)

interface AuthService {
    /**
     * @throws ApiException 401 `invalid_credentials` for every failure, whatever
     *   the underlying reason.
     */
    fun login(email: String, password: String, ipAddress: String?, userAgent: String?): LoginResult

    /** Resolves a session token to its caller, or null if it isn't usable. */
    fun resolvePrincipal(token: String): UserPrincipal?

    fun logout(sessionId: UUID)

    /** Ends every session for the user, not just the one making the request. */
    fun logoutAll(userId: UUID)

    fun findUser(userId: UUID): AuthUserResponse?

    /**
     * Deletes session rows that are already past their expiry. Housekeeping only —
     * an expired session is refused at lookup regardless. Returns the number removed.
     */
    fun purgeExpiredSessions(): Int
}

class AuthServiceImpl(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val passwordService: PasswordService,
    private val tokenService: TokenService
) : AuthService {

    private val logger = LoggerFactory.getLogger(AuthServiceImpl::class.java)

    override fun login(
        email: String,
        password: String,
        ipAddress: String?,
        userAgent: String?
    ): LoginResult {
        val now = Instant.now()
        val user = userRepository.findByEmail(email)

        if (user == null) {
            // Spend comparable time to a real verification before failing, so
            // that response latency doesn't disclose which emails have accounts.
            passwordService.dummyVerify()
            throw invalidCredentials("no user for email")
        }

        if (user.lockedUntil != null && user.lockedUntil.isAfter(now)) {
            throw invalidCredentials("account locked until ${user.lockedUntil} (user ${user.id})")
        }

        if (!passwordService.verify(password, user.passwordHash)) {
            userRepository.recordFailedLogin(
                userId = user.id,
                lockThreshold = BackendConstants.Auth.MAX_FAILED_LOGIN_ATTEMPTS,
                lockedUntil = now.plus(BackendConstants.Auth.LOCKOUT_DURATION)
            )
            throw invalidCredentials("wrong password (user ${user.id})")
        }

        // Checked only after the password verifies: an attacker who doesn't know
        // the password learns nothing about the account's status either way.
        if (user.status != BackendConstants.UserStatus.ACTIVE) {
            throw invalidCredentials("status is '${user.status}' (user ${user.id})")
        }

        userRepository.recordSuccessfulLogin(user.id, now)

        val token = tokenService.generate()
        sessionRepository.create(
            userId = user.id,
            tokenHash = tokenService.hash(token),
            expiresAt = now.plus(BackendConstants.Auth.SESSION_TTL),
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        logger.info("Login succeeded for user {}", user.id)
        return LoginResult(token = token, user = user.toResponse())
    }

    override fun resolvePrincipal(token: String): UserPrincipal? {
        val session = sessionRepository.findActiveByTokenHash(tokenService.hash(token)) ?: return null

        sessionRepository.touch(session.sessionId, Instant.now())

        return UserPrincipal(
            userId = session.userId,
            companyId = session.companyId,
            roleName = session.roleName,
            sessionId = session.sessionId
        )
    }

    override fun logout(sessionId: UUID) {
        sessionRepository.revoke(sessionId, Instant.now())
    }

    override fun logoutAll(userId: UUID) {
        val revoked = sessionRepository.revokeAllForUser(userId, Instant.now())
        // Worth a line of its own: request logs show that logout-all was called,
        // but not how many sessions it actually ended.
        logger.info("Revoked all {} session(s) for user {}", revoked, userId)
    }

    override fun findUser(userId: UUID): AuthUserResponse? {
        return userRepository.findById(userId)?.toResponse()
    }

    override fun purgeExpiredSessions(): Int {
        return sessionRepository.deleteExpired(Instant.now())
    }

    /**
     * Builds the one error every failed login returns, and logs the actual cause
     * where operators can see it — CallLogging's `%X{requestId}` MDC ties the log
     * line back to the request that produced the opaque response.
     */
    private fun invalidCredentials(reason: String): ApiException {
        logger.info("Login failed: {}", reason)
        return ApiException(
            HttpStatusCode.Unauthorized,
            BackendConstants.Errors.INVALID_CREDENTIALS_CODE,
            BackendConstants.Errors.INVALID_CREDENTIALS_MESSAGE
        )
    }

    private fun AuthUserRecord.toResponse() = AuthUserResponse(
        id = id.toString(),
        email = email,
        fullName = fullName,
        companyId = companyId.toString(),
        role = roleName,
        status = status
    )
}
