package cloud.angora.constants

import java.time.Duration

object BackendConstants {
    object Routes {
        const val HEALTH_PATH = "/api/health"
        const val DISCORD_BASE = "/api/discord"
        const val DISCORD_SERVERS = "/servers"
        const val DISCORD_SERVERS_BY_ID = "/servers/{id}"
        const val DISCORD_SERVERS_LEAVE = "/servers/{id}/leave"
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
    }
}
