package cloud.angora

import cloud.angora.constants.BackendConstants
import cloud.angora.plugins.configureErrorHandling
import cloud.angora.plugins.configureHttp
import cloud.angora.plugins.configureMonitoring
import cloud.angora.plugins.configureSecurity
import cloud.angora.routes.authRoutes
import cloud.angora.routes.discordRoutes
import cloud.angora.routes.healthRoutes
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

    startExpiredSessionSweep(dependencies.authService)

    routing {
        // Deliberately unauthenticated: container healthchecks poll it.
        healthRoutes(dependencies.healthService)
        authRoutes(dependencies.authService)
        discordRoutes(dependencies.discordService)
    }
}

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
