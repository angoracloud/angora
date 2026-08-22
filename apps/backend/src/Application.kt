package cloud.angora

import cloud.angora.constants.BackendConstants
import cloud.angora.dto.ApiError
import cloud.angora.dto.ApiErrorEnvelope
import cloud.angora.error.ApiException
import cloud.angora.repository.DiscordRepositoryImpl
import cloud.angora.repository.HealthRepositoryImpl
import cloud.angora.routes.discordRoutes
import cloud.angora.routes.healthRoutes
import cloud.angora.service.DiscordServiceImpl
import cloud.angora.service.HealthServiceImpl
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.event.Level
import java.util.UUID

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

    // Services (Business Logic Layer)
    val healthService = HealthServiceImpl(healthRepository)
    val discordService = DiscordServiceImpl(
        discordRepository = discordRepository,
        clientId = discordClientId,
        botUrl = discordBotUrl
    )

    // Routing (API / Controller Layer)
    routing {
        healthRoutes(healthService)
        discordRoutes(discordService)
    }
}

