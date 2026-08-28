package cloud.angora.service

import cloud.angora.constants.BackendConstants
import cloud.angora.dto.*
import cloud.angora.repository.DiscordRepository
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface DiscordService {
    fun getAllServers(): List<DiscordServerDto>
    /**
     * Optimistically marks the server as left in the database and notifies the Discord bot service.
     *
     * The HTTP notification to the bot is dispatched asynchronously with a timeout and error handling.
     * If the bot is temporarily offline or unreachable, the bot's periodic background reconciliation
     * (running via `syncAllGuilds` every 60s) will reconcile the true status automatically.
     */
    fun leaveServer(idOrGuildId: String): DeleteServerResponse?
    fun deleteServer(idOrGuildId: String): DeleteServerResponse?
    fun syncGuild(req: SyncGuildRequest)
    fun getInviteInfo(): DiscordInviteResponse
}

class DiscordServiceImpl(
    private val discordRepository: DiscordRepository,
    private val clientId: String,
    private val botUrl: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient()
) : DiscordService {
    private val logger = LoggerFactory.getLogger(DiscordServiceImpl::class.java)

    override fun getAllServers(): List<DiscordServerDto> {
        return discordRepository.findAll()
    }

    override fun leaveServer(idOrGuildId: String): DeleteServerResponse? {
        val targetGuildId = discordRepository.markServerLeft(idOrGuildId) ?: return null

        notifyBotToLeaveGuild(targetGuildId)

        return DeleteServerResponse(status = "updated", guildId = targetGuildId, botJoined = false)
    }

    override fun deleteServer(idOrGuildId: String): DeleteServerResponse? {
        val targetGuildId = discordRepository.softDelete(idOrGuildId) ?: return null

        notifyBotToLeaveGuild(targetGuildId)

        return DeleteServerResponse(status = "deleted", guildId = targetGuildId, botJoined = false)
    }

    override fun syncGuild(req: SyncGuildRequest) {
        discordRepository.upsertSyncedGuild(req)
    }

    override fun getInviteInfo(): DiscordInviteResponse {
        val permissions = BackendConstants.Discord.DEFAULT_PERMISSIONS
        val scopes = BackendConstants.Discord.OAUTH_SCOPES
        val authBaseUrl = BackendConstants.Discord.OAUTH_AUTHORIZE_URL
        val url = "$authBaseUrl?client_id=$clientId&scope=$scopes&permissions=$permissions"
        return DiscordInviteResponse(inviteUrl = url, clientId = clientId)
    }

    /**
     * Dispatches an asynchronous HTTP notification to the Discord bot to leave the target guild.
     *
     * Uses a configurable timeout and attaches [java.util.concurrent.CompletableFuture.whenComplete]
     * to ensure any network or downstream errors are captured and logged instead of failing silently.
     */
    private fun notifyBotToLeaveGuild(guildId: String) {
        try {
            val leavePrefix = BackendConstants.Discord.BOT_LEAVE_ENDPOINT_PREFIX
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$botUrl$leavePrefix/$guildId"))
                .timeout(BackendConstants.Discord.HTTP_REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete { response, error ->
                    if (error != null) {
                        logger.warn("Failed to notify Discord bot to leave guild $guildId (bot service may be offline or unreachable): ${error.message}", error)
                    } else if (response != null && response.statusCode() !in 200..299) {
                        logger.warn("Discord bot returned non-2xx status code ${response.statusCode()} when leaving guild $guildId: ${response.body()}")
                    } else {
                        logger.debug("Successfully notified Discord bot to leave guild $guildId")
                    }
                }
        } catch (e: Exception) {
            logger.warn("Failed to dispatch leave notification to Discord bot for guild $guildId", e)
        }
    }
}


