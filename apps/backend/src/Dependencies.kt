package cloud.angora

import cloud.angora.config.SecretsProvider
import cloud.angora.constants.BackendConstants
import cloud.angora.repository.DiscordRepositoryImpl
import cloud.angora.repository.HealthRepositoryImpl
import cloud.angora.repository.ServiceTokenRepositoryImpl
import cloud.angora.repository.SessionRepositoryImpl
import cloud.angora.repository.UserRepositoryImpl
import cloud.angora.service.AuthService
import cloud.angora.service.AuthServiceImpl
import cloud.angora.service.DiscordService
import cloud.angora.service.DiscordServiceImpl
import cloud.angora.service.HealthService
import cloud.angora.service.HealthServiceImpl
import cloud.angora.service.PasswordServiceImpl
import cloud.angora.service.ServiceTokenService
import cloud.angora.service.ServiceTokenServiceImpl
import cloud.angora.service.TokenServiceImpl
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Wires repositories and services together for one database connection.
 *
 * Only services are exposed: routes and application setup talk to those, never to
 * a repository directly, which is what keeps the N-tier boundary from eroding at
 * the wiring layer.
 */
class Dependencies(database: Database, secrets: SecretsProvider) {

    private val healthRepository = HealthRepositoryImpl(database)
    private val discordRepository = DiscordRepositoryImpl(database)
    private val userRepository = UserRepositoryImpl(database)
    private val sessionRepository = SessionRepositoryImpl(database)
    private val serviceTokenRepository = ServiceTokenRepositoryImpl(database)

    private val passwordService = PasswordServiceImpl()
    private val tokenService = TokenServiceImpl()

    val healthService: HealthService = HealthServiceImpl(healthRepository)

    val discordService: DiscordService = DiscordServiceImpl(
        discordRepository = discordRepository,
        clientId = secrets.get(
            BackendConstants.Discord.CLIENT_ID_ENV,
            BackendConstants.Discord.DEFAULT_CLIENT_ID
        ),
        botUrl = secrets.get(
            BackendConstants.Discord.BOT_URL_ENV,
            BackendConstants.Discord.DEFAULT_BOT_URL
        )
    )

    val authService: AuthService = AuthServiceImpl(
        userRepository = userRepository,
        sessionRepository = sessionRepository,
        passwordService = passwordService,
        tokenService = tokenService
    )

    val serviceTokenService: ServiceTokenService =
        ServiceTokenServiceImpl(serviceTokenRepository, tokenService)
}
