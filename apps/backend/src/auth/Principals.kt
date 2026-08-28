package cloud.angora.auth

import java.util.UUID

/**
 * An authenticated human, resolved from the session cookie on every request.
 * The role is read fresh from the database each time rather than carried in the
 * cookie, so a role change or suspension takes effect immediately.
 */
data class UserPrincipal(
    val userId: UUID,
    val companyId: UUID,
    val roleName: String,
    val sessionId: UUID
)

/** An authenticated machine caller (a bot), resolved from a bearer service token. */
data class ServicePrincipal(
    val tokenId: UUID,
    val name: String
)
