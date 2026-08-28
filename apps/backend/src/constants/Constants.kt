package cloud.angora.constants

import java.time.Duration
import java.util.regex.Pattern

object BackendConstants {
    /** Path literals with no single owner, kept here so they aren't retyped per feature. */
    object Paths {
        const val ROOT = "/"
    }

    object Routes {
        const val HEALTH_PATH = "/api/health"
        const val DISCORD_BASE = "/api/discord"
        const val DISCORD_SERVERS = "/servers"
        const val DISCORD_SERVERS_BY_ID = "/servers/{id}"
        const val DISCORD_SERVERS_LEAVE = "/servers/{id}/leave"
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

        /**
         * The login throttle, read as a pair: [LOGIN_RATE_LIMIT] attempts are
         * allowed per caller per [LOGIN_RATE_LIMIT_WINDOW] — 10 per minute — after
         * which the endpoint answers 429 until the window refills.
         *
         * This complements [MAX_FAILED_LOGIN_ATTEMPTS]/[LOCKOUT_DURATION] rather
         * than duplicating it: the lockout stops one account being hammered, this
         * stops one client working through many accounts.
         */
        const val LOGIN_RATE_LIMIT = 10

        /** The refill period for [LOGIN_RATE_LIMIT]. See its doc for the pair. */
        val LOGIN_RATE_LIMIT_WINDOW: Duration = Duration.ofMinutes(1)

        const val LOGIN_RATE_LIMITER_NAME = "login"

        /** The `status` values `/logout` and `/logout-all` return. Part of the API contract. */
        object LogoutStatus {
            const val CURRENT_SESSION = "logged_out"
            const val ALL_SESSIONS = "logged_out_everywhere"
        }
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

        /** The `status` values returned in Discord server responses. Part of the API contract. */
        object ServerStatus {
            const val UPDATED = "updated"
            const val DELETED = "deleted"
            const val SYNCED = "synced"
        }
    }

    object DatabaseDefaults {
        const val DRIVER_CLASS = "org.postgresql.Driver"
    }

    object Errors {
        const val MISSING_SERVER_ID_CODE = "missing_server_id"
        const val MISSING_SERVER_ID_MESSAGE = "Missing server ID"
        const val SERVER_NOT_FOUND_CODE = "server_not_found"
        const val SERVER_NOT_FOUND_MESSAGE = "Server not found"
        const val VALIDATION_ERROR_CODE = "validation_error"
        const val VALIDATION_ERROR_MESSAGE = "Request validation failed"
        const val NOT_FOUND_CODE = "not_found"
        const val NOT_FOUND_MESSAGE = "The requested resource was not found"
        const val INTERNAL_ERROR_CODE = "internal_error"
        const val INTERNAL_ERROR_MESSAGE = "An unexpected error occurred"

        /**
         * The single response for every failed login — unknown email, wrong
         * password, locked, suspended, deactivated, or soft-deleted alike.
         * Distinguishing them would tell an attacker which addresses hold
         * accounts. The real reason is logged server-side against the request id.
         */
        const val INVALID_CREDENTIALS_CODE = "invalid_credentials"
        const val INVALID_CREDENTIALS_MESSAGE = "Invalid email or password"

        /**
         * Why a login actually failed, for the server-side log only — **never**
         * for a response. Every one of these paths answers the caller with
         * [INVALID_CREDENTIALS_CODE]/[INVALID_CREDENTIALS_MESSAGE]; that
         * uniformity is what stops the endpoint disclosing which addresses hold
         * accounts. Don't pass one of these to an `ApiException`.
         *
         * The templated ones are `String.format` patterns, not display text.
         */
        object LoginFailureReasons {
            const val NO_USER_FOR_EMAIL = "no user for email"

            /** `%s` = locked-until instant, `%s` = user id. */
            const val ACCOUNT_LOCKED = "account locked until %s (user %s)"

            /** `%s` = user id. */
            const val WRONG_PASSWORD = "wrong password (user %s)"

            /** `%s` = status, `%s` = user id. */
            const val INACTIVE_STATUS = "status is '%s' (user %s)"
        }

        const val UNAUTHENTICATED_CODE = "unauthenticated"
        const val UNAUTHENTICATED_MESSAGE = "Authentication required"

        const val INVALID_REQUEST_BODY_CODE = "invalid_request_body"
        const val INVALID_REQUEST_BODY_MESSAGE = "Request body is missing or malformed"

        const val RATE_LIMITED_CODE = "rate_limited"
        const val RATE_LIMITED_MESSAGE = "Too many requests, please try again later"
    }

    object Validation {
        object Limits {
            const val MAX_EMAIL_LENGTH = 320
            const val MAX_GUILD_ID_LENGTH = 64
            const val MAX_NAME_LENGTH = 255
            const val MAX_ICON_URL_LENGTH = 512
            const val MAX_OWNER_ID_LENGTH = 64
            const val MIN_MEMBER_COUNT = 0
            const val MAX_SERVER_ID_LENGTH = 64
        }

        object Patterns {
            const val EMAIL_REGEX_PATTERN = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            val EMAIL_REGEX: Pattern = Pattern.compile(EMAIL_REGEX_PATTERN)
            val ALLOWED_URL_SCHEMES = setOf("http", "https")
        }

        object Messages {
            fun mustNotBeBlank(fieldName: String): String = "$fieldName must not be blank"
            fun cannotExceedMaxLength(fieldName: String, maxLength: Int): String = "$fieldName cannot exceed $maxLength characters"
            fun mustBeAtLeastMinLength(fieldName: String, minLength: Int): String = "$fieldName must be at least $minLength characters"
            fun mustBeValidEmail(fieldName: String): String = "$fieldName must be a valid email address"
            fun mustBeValidUuid(fieldName: String): String = "$fieldName must be a valid UUID"
            fun mustBeZeroOrPositive(fieldName: String): String = "$fieldName must be zero or positive"
            fun mustBeBetween(fieldName: String, min: Int, max: Int): String = "$fieldName must be between $min and $max"
            fun mustBeValidHttpOrHttpsUrl(fieldName: String): String = "$fieldName must be a valid http or https URL"
            fun mustBeValidUrl(fieldName: String): String = "$fieldName must be a valid URL"
        }

        object Fields {
            const val GUILD_ID = "guildId"
            const val NAME = "name"
            const val ICON_URL = "iconUrl"
            const val OWNER_ID = "ownerId"
            const val MEMBER_COUNT = "memberCount"
            const val EMAIL = "email"
            const val ID = "id"
        }
    }
}
