package cloud.angora.dto

import kotlinx.serialization.Serializable

@Serializable
data class DiscordServerDto(
    val id: String,
    val guildId: String,
    val name: String,
    val iconUrl: String? = null,
    val ownerId: String? = null,
    val memberCount: Int = 0,
    val botJoined: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SyncGuildRequest(
    val guildId: String,
    val name: String,
    val iconUrl: String? = null,
    val ownerId: String? = null,
    val memberCount: Int = 0,
    val botJoined: Boolean = true
)

@Serializable
data class DeleteServerResponse(
    val status: String,
    val guildId: String? = null,
    val botJoined: Boolean = false
)


@Serializable
data class DiscordInviteResponse(
    val inviteUrl: String,
    val clientId: String
)

@Serializable
data class SyncStatusResponse(
    val status: String = "synced"
)
