package cloud.angora

import cloud.angora.constants.BackendConstants
import cloud.angora.plugins.configureErrorHandling
import cloud.angora.plugins.configureHttp
import cloud.angora.plugins.configureMonitoring
import cloud.angora.plugins.configureSecurity
import cloud.angora.routes.authRoutes
import cloud.angora.routes.discordRoutes
import cloud.angora.routes.healthRoutes
import cloud.angora.service.DiscordServiceImpl
import cloud.angora.service.HealthServiceImpl
import cloud.angora.validation.configureRequestValidation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import cloud.angora.service.AuthService
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.toKotlinDuration

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val database = connectDatabase()
    val dependencies = Dependencies(database)

    configureMonitoring()
    configureErrorHandling()
    configureHttp()
    configureSecurity(dependencies.authService, dependencies.serviceTokenService)

    dependencies.serviceTokenService.register(
        name = BackendConstants.Auth.DISCORD_BOT_TOKEN_NAME,
        token = System.getenv(BackendConstants.Auth.SERVICE_TOKEN_DISCORD_BOT_ENV)
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
        exception<RequestValidationException> { call, cause ->
            val message = if (cause.reasons.isNotEmpty()) {
                cause.reasons.joinToString("; ")
            } else {
                BackendConstants.Errors.VALIDATION_ERROR_MESSAGE
            }
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.VALIDATION_ERROR_CODE,
                        message,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.BAD_REQUEST_CODE,
                        cause.message?.substringBefore("\n") ?: BackendConstants.Errors.BAD_REQUEST_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
        exception<SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.INVALID_JSON_CODE,
                        BackendConstants.Errors.INVALID_JSON_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
        exception<CannotTransformContentToTypeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.INVALID_JSON_CODE,
                        BackendConstants.Errors.INVALID_JSON_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception processing ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.INTERNAL_ERROR_CODE,
                        BackendConstants.Errors.INTERNAL_ERROR_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ApiErrorEnvelope(
                    ApiError(
                        BackendConstants.Errors.NOT_FOUND_CODE,
                        BackendConstants.Errors.NOT_FOUND_MESSAGE,
                        call.callId ?: "unknown"
                    )
                )
            )
        }
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowNonSimpleContentTypes = true
    }
    startExpiredSessionSweep(dependencies.authService)

    routing {
        // Deliberately unauthenticated: container healthchecks poll it.
        healthRoutes(dependencies.healthService)
        authRoutes(dependencies.authService)
        discordRoutes(dependencies.discordService)
    }
}

    install(RequestValidation) {
        configureRequestValidation()
    }

    val discordClientId = System.getenv("DISCORD_CLIENT_ID") ?: BackendConstants.Discord.DEFAULT_CLIENT_ID
    val discordBotUrl = System.getenv("DISCORD_BOT_URL") ?: BackendConstants.Discord.DEFAULT_BOT_URL
/**
 * Runs Flyway, then opens the Exposed connection — in that order, so the schema is
 * always current before any query can run.
 */
private fun Application.connectDatabase(): Database {
    val url = environment.config.property("database.url").getString()
    val user = environment.config.property("database.user").getString()
    val password = environment.config.property("database.password").getString()

    Flyway.configure()
        .dataSource(url, user, password)
        .load()
        .migrate()

    return Database.connect(
        url = url,
        driver = BackendConstants.DatabaseDefaults.DRIVER_CLASS,
        user = user,
        password = password
    )
}

/**
 * Housekeeping only: expired sessions are already refused at lookup time, so this
 * just stops the table growing without bound.
 */
private fun Application.startExpiredSessionSweep(authService: AuthService) {
    launch {
        while (isActive) {
            try {
                val removed = authService.purgeExpiredSessions()
                if (removed > 0) {
                    log.info("Swept {} expired session(s)", removed)
                }
            } catch (e: Exception) {
                log.warn("Expired-session sweep failed; retrying next interval", e)
            }
            delay(BackendConstants.Auth.SESSION_SWEEP_INTERVAL.toKotlinDuration())
        }
    }
}
