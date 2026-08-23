package cloud.angora

import cloud.angora.auth.AngoraSession
import cloud.angora.constants.BackendConstants
import cloud.angora.dto.ApiError
import cloud.angora.dto.ApiErrorEnvelope
import cloud.angora.error.ApiException
import cloud.angora.repository.DiscordRepositoryImpl
import cloud.angora.repository.HealthRepositoryImpl
import cloud.angora.repository.ServiceTokenRepositoryImpl
import cloud.angora.repository.SessionRepositoryImpl
import cloud.angora.repository.UserRepositoryImpl
import cloud.angora.routes.authRoutes
import cloud.angora.routes.discordRoutes
import cloud.angora.routes.healthRoutes
import cloud.angora.service.AuthServiceImpl
import cloud.angora.service.DiscordServiceImpl
import cloud.angora.service.HealthServiceImpl
import cloud.angora.service.PasswordServiceImpl
import cloud.angora.service.ServiceTokenServiceImpl
import cloud.angora.service.TokenServiceImpl
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
// Aliased because this file's own package holds the Exposed `Sessions` table
// object, which would otherwise shadow Ktor's plugin of the same name.
import io.ktor.server.sessions.Sessions as SessionsPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.event.Level
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlin.time.toKotlinDuration

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val dbUrl = environment.config.property("database.url").getString()
    val dbUser = environment.config.property("database.user").getString()
    val dbPassword = environment.config.property("database.password").getString()

    Flyway.configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .load()
        .migrate()

    val database = Database.connect(
        url = dbUrl,
        driver = BackendConstants.DatabaseDefaults.DRIVER_CLASS,
        user = dbUser,
        password = dbPassword
    )

    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { it.isNotBlank() }
        generate { UUID.randomUUID().toString() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.statusCode,
                ApiErrorEnvelope(ApiError(cause.code, cause.message, call.callId ?: "unknown"))
            )
        }
        // A body that is absent, unparseable, or sent without a usable
        // Content-Type. Ktor raises two unrelated types for these
        // (BadRequestException and ContentTransformationException, whose subclass
        // UnsupportedMediaTypeException covers the header case), and both need
        // handling — otherwise they fall through to the Throwable handler below
        // and get reported as a 500, blaming the server for a client mistake.
        val malformedRequest: suspend (ApplicationCall, Throwable) -> Unit = { call, cause ->
            call.application.log.info("Rejected malformed request to ${call.request.uri}: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.INVALID_REQUEST_BODY_CODE,
                        BackendConstants.Errors.INVALID_REQUEST_BODY_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
        exception<BadRequestException> { call, cause -> malformedRequest(call, cause) }
        exception<ContentTransformationException> { call, cause -> malformedRequest(call, cause) }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception processing ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorEnvelope(ApiError("internal_error", "An unexpected error occurred", call.callId ?: "unknown"))
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiErrorEnvelope(ApiError("not_found", "The requested resource was not found", call.callId ?: "unknown"))
            )
        }
        // The RateLimit plugin rejects before any handler runs, so without this a
        // throttled login would be the one response in the API with no envelope.
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respond(
                status,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.RATE_LIMITED_CODE,
                        BackendConstants.Errors.RATE_LIMITED_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
    }

    // No anyHost(): the session cookie makes every request credentialed, and Ktor
    // refuses to pair a wildcard origin with allowCredentials. Normally empty,
    // since nginx serves the frontend same-origin; it exists for `pnpm dev`.
    val allowedOrigins = (System.getenv(BackendConstants.Auth.CORS_ALLOWED_ORIGINS_ENV) ?: "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    install(CORS) {
        allowedOrigins.forEach { origin ->
            val url = URI(origin)
            allowHost("${url.host}${if (url.port != -1) ":${url.port}" else ""}", schemes = listOf(url.scheme))
        }
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowNonSimpleContentTypes = true
        allowCredentials = true
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            encodeDefaults = true
        })
    }

    val discordClientId = System.getenv("DISCORD_CLIENT_ID") ?: BackendConstants.Discord.DEFAULT_CLIENT_ID
    val discordBotUrl = System.getenv("DISCORD_BOT_URL") ?: BackendConstants.Discord.DEFAULT_BOT_URL

    // Repositories (Data Access Layer)
    val healthRepository = HealthRepositoryImpl(database)
    val discordRepository = DiscordRepositoryImpl(database)
    val userRepository = UserRepositoryImpl(database)
    val sessionRepository = SessionRepositoryImpl(database)
    val serviceTokenRepository = ServiceTokenRepositoryImpl(database)

    // Services (Business Logic Layer)
    val healthService = HealthServiceImpl(healthRepository)
    val discordService = DiscordServiceImpl(
        discordRepository = discordRepository,
        clientId = discordClientId,
        botUrl = discordBotUrl
    )
    val passwordService = PasswordServiceImpl()
    val tokenService = TokenServiceImpl()
    val authService = AuthServiceImpl(
        userRepository = userRepository,
        sessionRepository = sessionRepository,
        passwordService = passwordService,
        tokenService = tokenService
    )
    val serviceTokenService = ServiceTokenServiceImpl(serviceTokenRepository, tokenService)

    serviceTokenService.register(
        name = BackendConstants.Auth.DISCORD_BOT_TOKEN_NAME,
        token = System.getenv(BackendConstants.Auth.SERVICE_TOKEN_DISCORD_BOT_ENV)
    )

    // Housekeeping only: expired sessions are already refused at lookup time, so
    // this just stops the table growing without bound.
    launch {
        while (isActive) {
            try {
                val removed = sessionRepository.deleteExpired(Instant.now())
                if (removed > 0) {
                    log.info("Swept {} expired session(s)", removed)
                }
            } catch (e: Exception) {
                log.warn("Expired-session sweep failed; retrying next interval", e)
            }
            delay(BackendConstants.Auth.SESSION_SWEEP_INTERVAL.toKotlinDuration())
        }
    }

    install(SessionsPlugin) {
        cookie<AngoraSession>(BackendConstants.Auth.COOKIE_NAME) {
            cookie.path = BackendConstants.Auth.COOKIE_PATH
            cookie.httpOnly = true
            // Off for plain-HTTP local dev; anything with TLS in front must set it.
            cookie.secure = System.getenv(BackendConstants.Auth.COOKIE_SECURE_ENV)?.toBoolean() ?: false
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
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorEnvelope(
                        ApiError(
                            BackendConstants.Errors.UNAUTHENTICATED_CODE,
                            BackendConstants.Errors.UNAUTHENTICATED_MESSAGE,
                            call.callId ?: "unknown"
                        )
                    )
                )
            }
        }

        bearer(BackendConstants.Auth.SERVICE_PROVIDER) {
            authenticate { credential -> serviceTokenService.verify(credential.token) }
        }
    }

    install(RateLimit) {
        register(RateLimitName(BackendConstants.Auth.LOGIN_RATE_LIMITER_NAME)) {
            rateLimiter(
                limit = BackendConstants.Auth.LOGIN_RATE_LIMIT,
                refillPeriod = BackendConstants.Auth.LOGIN_RATE_LIMIT_WINDOW.toKotlinDuration()
            )
        }
    }

    // Routing (API / Controller Layer)
    routing {
        // Deliberately unauthenticated: container healthchecks poll it.
        healthRoutes(healthService)
        authRoutes(authService)
        discordRoutes(discordService)
    }
}

