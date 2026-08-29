package cloud.angora.plugins

import cloud.angora.auth.AngoraSession
import cloud.angora.config.SecretsProvider
import cloud.angora.constants.BackendConstants
import cloud.angora.error.respondError
import cloud.angora.service.AuthService
import cloud.angora.service.ServiceTokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.session
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
// Aliased because `cloud.angora.Sessions` — the Exposed table — would otherwise
// shadow Ktor's plugin of the same name.
import io.ktor.server.sessions.Sessions as SessionsPlugin
import kotlin.time.toKotlinDuration

/**
 * The two authentication realms and the login rate limiter.
 *
 * `USER_PROVIDER` is for humans: an opaque token in a cookie, resolved against the
 * `sessions` table on every request. `SERVICE_PROVIDER` is for machines (the bots):
 * a bearer service token, with no user and no role. They are entirely separate — a
 * cookie is rejected on a service route and vice versa.
 */
fun Application.configureSecurity(
    authService: AuthService,
    serviceTokenService: ServiceTokenService,
    secrets: SecretsProvider
) {
    install(SessionsPlugin) {
        cookie<AngoraSession>(BackendConstants.Auth.COOKIE_NAME) {
            cookie.path = BackendConstants.Paths.ROOT
            cookie.httpOnly = true
            // Off for plain-HTTP local dev; anything with TLS in front must set it.
            cookie.secure = secrets.get(BackendConstants.Auth.COOKIE_SECURE_ENV)?.toBoolean() ?: false
            cookie.maxAgeInSeconds = BackendConstants.Auth.SESSION_TTL.seconds
            cookie.extensions["SameSite"] = BackendConstants.Auth.COOKIE_SAME_SITE
        }
    }

    install(Authentication) {
        session<AngoraSession>(BackendConstants.Auth.USER_PROVIDER) {
            validate { session -> authService.resolvePrincipal(session.token) }
            challenge {
                // Clear the cookie: it names a session the server no longer
                // honours, so keeping it just makes the browser retry with a
                // credential that can never work.
                call.sessions.clear<AngoraSession>()
                call.respondError(
                    HttpStatusCode.Unauthorized,
                    BackendConstants.Errors.UNAUTHENTICATED_CODE,
                    BackendConstants.Errors.UNAUTHENTICATED_MESSAGE
                )
            }
        }

        bearer(BackendConstants.Auth.SERVICE_PROVIDER) {
            authenticate { credential -> serviceTokenService.verify(credential.token) }
        }
    }

    // Complements the per-account lockout: the lockout stops one account being
    // hammered, this stops one client working through many.
    install(RateLimit) {
        register(RateLimitName(BackendConstants.Auth.LOGIN_RATE_LIMITER_NAME)) {
            rateLimiter(
                limit = BackendConstants.Auth.LOGIN_RATE_LIMIT,
                refillPeriod = BackendConstants.Auth.LOGIN_RATE_LIMIT_WINDOW.toKotlinDuration()
            )
        }
    }
}
