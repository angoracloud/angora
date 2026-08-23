package cloud.angora.routes

import cloud.angora.auth.AngoraSession
import cloud.angora.auth.requireUser
import cloud.angora.constants.BackendConstants
import cloud.angora.dto.LoginRequest
import cloud.angora.dto.LogoutResponse
import cloud.angora.error.ApiException
import cloud.angora.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

fun Route.authRoutes(authService: AuthService) {
    route(BackendConstants.Routes.AUTH_BASE) {

        // Rate-limited independently of the per-account lockout: the lockout stops
        // one account being hammered, this stops one client working through many.
        rateLimit(RateLimitName(BackendConstants.Auth.LOGIN_RATE_LIMITER_NAME)) {
            post(BackendConstants.Routes.AUTH_LOGIN) {
                // A malformed body surfaces as BadRequestException, which
                // StatusPages maps to the standard 400 envelope.
                val request = call.receive<LoginRequest>()

                val result = authService.login(
                    email = request.email,
                    password = request.password,
                    ipAddress = call.request.origin.remoteAddress,
                    userAgent = call.request.userAgent()
                )

                call.sessions.set(AngoraSession(token = result.token))
                call.respond(HttpStatusCode.OK, result.user)
            }
        }

        authenticate(BackendConstants.Auth.USER_PROVIDER) {
            get(BackendConstants.Routes.AUTH_ME) {
                val principal = call.requireUser()

                val user = authService.findUser(principal.userId)
                    ?: throw ApiException(
                        HttpStatusCode.Unauthorized,
                        BackendConstants.Errors.UNAUTHENTICATED_CODE,
                        BackendConstants.Errors.UNAUTHENTICATED_MESSAGE
                    )

                call.respond(HttpStatusCode.OK, user)
            }

            post(BackendConstants.Routes.AUTH_LOGOUT) {
                val principal = call.requireUser()

                authService.logout(principal.sessionId)
                call.sessions.clear<AngoraSession>()

                call.respond(HttpStatusCode.OK, LogoutResponse(status = "logged_out"))
            }

            post(BackendConstants.Routes.AUTH_LOGOUT_ALL) {
                val principal = call.requireUser()

                authService.logoutAll(principal.userId)
                call.sessions.clear<AngoraSession>()

                call.respond(HttpStatusCode.OK, LogoutResponse(status = "logged_out_everywhere"))
            }
        }
    }
}
