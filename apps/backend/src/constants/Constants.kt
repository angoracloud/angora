package cloud.angora.constants

import java.time.Duration
import java.util.regex.Pattern

object BackendConstants {
    object Routes {
        const val HEALTH_PATH = "/api/health"
        const val DISCORD_BASE = "/api/discord"
        const val DISCORD_SERVERS = "/servers"
        const val DISCORD_SERVERS_BY_ID = "/servers/{id}"
        const val DISCORD_BOT_SYNC = "/bot/sync"
        const val DISCORD_BOT_INVITE = "/bot/invite"
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
        const val VALIDATION_ERROR_CODE = "validation_error"
        const val VALIDATION_ERROR_MESSAGE = "Request validation failed"
        const val BAD_REQUEST_CODE = "bad_request"
        const val BAD_REQUEST_MESSAGE = "Invalid request body or parameters"
        const val INVALID_JSON_CODE = "invalid_json"
        const val INVALID_JSON_MESSAGE = "Malformed JSON or invalid request payload structure"
        const val NOT_FOUND_CODE = "not_found"
        const val NOT_FOUND_MESSAGE = "The requested resource was not found"
        const val INTERNAL_ERROR_CODE = "internal_error"
        const val INTERNAL_ERROR_MESSAGE = "An unexpected error occurred"
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
