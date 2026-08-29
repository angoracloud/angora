package cloud.angora

import cloud.angora.config.SecretsProvider
import cloud.angora.config.loadSecrets
import cloud.angora.constants.BackendConstants
import cloud.angora.plugins.configureErrorHandling
import cloud.angora.plugins.configureHttp
import cloud.angora.plugins.configureMonitoring
import cloud.angora.plugins.configureSecurity
import cloud.angora.plugins.configureValidation
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
    // First, because the database connection below is configured from it.
    val secrets = loadSecrets()

    val database = connectDatabase(secrets)
    val dependencies = Dependencies(database, secrets)

    configureMonitoring()
    configureErrorHandling()
    configureHttp(secrets)
    configureSecurity(dependencies.authService, dependencies.serviceTokenService, secrets)
    configureValidation()

    dependencies.serviceTokenService.register(
        name = BackendConstants.Auth.DISCORD_BOT_TOKEN_NAME,
        token = secrets.get(BackendConstants.Auth.SERVICE_TOKEN_DISCORD_BOT_ENV)
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
 *
 * Each setting comes from [secrets] first, then from `application.yaml`, whose
 * `${DB_URL:default}` substitution resolves the plain env var and the default.
 */
private fun Application.connectDatabase(secrets: SecretsProvider): Database {
    fun setting(secretName: String, configKey: String): String =
        secrets.get(secretName) ?: environment.config.property(configKey).getString()

    val url = setting(
        BackendConstants.DatabaseDefaults.URL_ENV,
        BackendConstants.DatabaseDefaults.URL_PROPERTY
    )
    val user = setting(
        BackendConstants.DatabaseDefaults.USER_ENV,
        BackendConstants.DatabaseDefaults.USER_PROPERTY
    )
    val password = setting(
        BackendConstants.DatabaseDefaults.PASSWORD_ENV,
        BackendConstants.DatabaseDefaults.PASSWORD_PROPERTY
    )

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
