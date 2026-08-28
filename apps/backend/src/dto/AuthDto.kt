package cloud.angora.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * The user as the API exposes them.
 *
 * Deliberately has no `passwordHash` or `twoFactorSecret` field — those columns
 * must never reach a response, and the way to guarantee that is for the
 * serializable type not to carry them at all.
 */
@Serializable
data class AuthUserResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val companyId: String,
    val role: String,
    val status: String
)

@Serializable
data class LogoutResponse(
    val status: String
)
