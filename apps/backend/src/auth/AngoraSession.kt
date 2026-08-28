package cloud.angora.auth

import kotlinx.serialization.Serializable

/**
 * What the session cookie carries: an opaque token and nothing else. The
 * `sessions` table holds everything else, so revocation is immediate.
 *
 * `@Serializable` is required — Ktor's cookie serializer resolves a kotlinx
 * serializer at plugin-install time and the app won't start without one.
 */
@Serializable
data class AngoraSession(val token: String)
