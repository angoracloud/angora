package cloud.angora.constants

import java.time.Duration

object BackendConstants {
    object Routes {
        const val HEALTH_PATH = "/api/health"
        const val DISCORD_BASE = "/api/discord"
        const val DISCORD_SERVERS = "/servers"
        const val DISCORD_SERVERS_BY_ID = "/servers/{id}"
        const val DISCORD_BOT_SYNC = "/bot/sync"
        const val DISCORD_BOT_INVITE = "/bot/invite"
        const val AUTH_BASE = "/api/auth"
        const val AUTH_LOGIN = "/login"
        const val AUTH_LOGOUT = "/logout"
        const val AUTH_LOGOUT_ALL = "/logout-all"
        const val AUTH_ME = "/me"
    }

    object Auth {
        /** Name of the authentication provider guarding human (session cookie) routes. */
        const val USER_PROVIDER = "user"

        /** Name of the authentication provider guarding machine (bearer token) routes. */
        const val SERVICE_PROVIDER = "service"

        const val COOKIE_NAME = "angora_session"
        const val COOKIE_PATH = "/"
        const val COOKIE_SAME_SITE = "Strict"

        val SESSION_TTL: Duration = Duration.ofDays(7)

        /**
         * How often expired session rows are swept.
         *
         * Purely housekeeping: an expired session is already refused at lookup
         * time, so this only stops the table growing without bound.
         */
        val SESSION_SWEEP_INTERVAL: Duration = Duration.ofHours(1)

        /** Entropy of session and service tokens, in bytes. 32 bytes = 256 bits. */
        const val TOKEN_BYTES = 32

        const val TOKEN_HASH_ALGORITHM = "SHA-256"

        /** Name of the `service_tokens` row the Discord bot authenticates with. */
        const val DISCORD_BOT_TOKEN_NAME = "discord-bot"

        const val SERVICE_TOKEN_DISCORD_BOT_ENV = "SERVICE_TOKEN_DISCORD_BOT"
        const val COOKIE_SECURE_ENV = "COOKIE_SECURE"
        const val CORS_ALLOWED_ORIGINS_ENV = "CORS_ALLOWED_ORIGINS"

        /** Consecutive failed logins before the account locks. */
        const val MAX_FAILED_LOGIN_ATTEMPTS = 5

        val LOCKOUT_DURATION: Duration = Duration.ofMinutes(15)

        /**
         * Argon2id parameters, at the OWASP-recommended second-choice baseline
         * (19 MiB memory, 2 iterations, 1 degree of parallelism). They are stored
         * inside each hash's PHC string, so raising them later only affects newly
         * written hashes — no migration, and old hashes keep verifying.
         */
        object Argon2 {
            const val MEMORY_KIB = 19456
            const val ITERATIONS = 2
            const val PARALLELISM = 1
            const val SALT_BYTES = 16
            const val HASH_BYTES = 32
            const val TYPE = "argon2id"
            const val VERSION = 19
        }

        /** Login attempts allowed per [LOGIN_RATE_LIMIT_WINDOW] before the endpoint throttles. */
        const val LOGIN_RATE_LIMIT = 10

        val LOGIN_RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(1)

        const val LOGIN_RATE_LIMITER_NAME = "login"
    }

    object Identity {
        const val PROVIDER_LOCAL = "local"
    }

    object UserStatus {
        const val ACTIVE = "active"
    }

    object RoleNames {
        const val OWNER = "owner"
        const val ADMIN = "admin"
        const val MEMBER = "member"
        const val CUSTOMER = "customer"
    }

    object Discord {
        const val DEFAULT_CLIENT_ID = "123456789012345678"
        const val DEFAULT_BOT_URL = "http://discord-bot:3001"
        const val OAUTH_AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
        const val OAUTH_SCOPES = "bot+applications.commands"
        // Minimal permissions: VIEW_CHANNEL (1024) + SEND_MESSAGES (2048) + EMBED_LINKS (16384) + READ_MESSAGE_HISTORY (65536) + USE_APPLICATION_COMMANDS (2147483648)
        const val DEFAULT_PERMISSIONS = "2147568640"
        const val BOT_LEAVE_ENDPOINT_PREFIX = "/leave"
        val HTTP_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(5)
    }

    object DatabaseDefaults {
        const val DRIVER_CLASS = "org.postgresql.Driver"
    }

    object Errors {
        const val MISSING_SERVER_ID_CODE = "missing_server_id"
        const val MISSING_SERVER_ID_MESSAGE = "Missing server ID"
        const val SERVER_NOT_FOUND_CODE = "server_not_found"
        const val SERVER_NOT_FOUND_MESSAGE = "Server not found"

        /**
         * The single response for every failed login — unknown email, wrong
         * password, locked, suspended, deactivated, or soft-deleted alike.
         * Distinguishing them would tell an attacker which addresses hold
         * accounts. The real reason is logged server-side against the request id.
         */
        const val INVALID_CREDENTIALS_CODE = "invalid_credentials"
        const val INVALID_CREDENTIALS_MESSAGE = "Invalid email or password"

        const val UNAUTHENTICATED_CODE = "unauthenticated"
        const val UNAUTHENTICATED_MESSAGE = "Authentication required"

        const val INVALID_REQUEST_BODY_CODE = "invalid_request_body"
        const val INVALID_REQUEST_BODY_MESSAGE = "Request body is missing or malformed"

        const val RATE_LIMITED_CODE = "rate_limited"
        const val RATE_LIMITED_MESSAGE = "Too many requests, please try again later"

        const val NOT_FOUND_CODE = "not_found"
        const val NOT_FOUND_MESSAGE = "The requested resource was not found"

        const val INTERNAL_ERROR_CODE = "internal_error"
        const val INTERNAL_ERROR_MESSAGE = "An unexpected error occurred"
    }
}
