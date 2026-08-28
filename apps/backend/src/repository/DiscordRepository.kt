package cloud.angora.repository

import cloud.angora.DiscordServers
import cloud.angora.dto.DiscordServerDto
import cloud.angora.dto.SyncGuildRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID

interface DiscordRepository {
    fun findAll(): List<DiscordServerDto>
    fun markServerLeft(idOrGuildId: String): String?
    fun softDelete(idOrGuildId: String): String?
    fun upsertSyncedGuild(req: SyncGuildRequest)
}

class DiscordRepositoryImpl(private val database: Database) : DiscordRepository {
    override fun findAll(): List<DiscordServerDto> {
        return transaction(database) {
            DiscordServers.selectAll()
                .where { DiscordServers.deletedAt.isNull() }
                .orderBy(DiscordServers.createdAt to SortOrder.DESC)
                .map { row ->
                    DiscordServerDto(
                        id = row[DiscordServers.id].value.toString(),
                        guildId = row[DiscordServers.guildId],
                        name = row[DiscordServers.name],
                        iconUrl = row[DiscordServers.iconUrl],
                        ownerId = row[DiscordServers.ownerId],
                        memberCount = row[DiscordServers.memberCount],
                        botJoined = row[DiscordServers.botJoined],
                        createdAt = row[DiscordServers.createdAt].toString(),
                        updatedAt = row[DiscordServers.updatedAt].toString()
                    )
                }
        }
    }


    override fun markServerLeft(idOrGuildId: String): String? {
        val now = Instant.now()
        return transaction(database) {
            val serverRow = try {
                val uuid = UUID.fromString(idOrGuildId)
                DiscordServers.selectAll()
                    .where { (DiscordServers.id eq uuid) and DiscordServers.deletedAt.isNull() }
                    .singleOrNull()
            } catch (e: IllegalArgumentException) {
                DiscordServers.selectAll()
                    .where { (DiscordServers.guildId eq idOrGuildId) and DiscordServers.deletedAt.isNull() }
                    .singleOrNull()
            }

            if (serverRow != null) {
                val gId = serverRow[DiscordServers.guildId]
                DiscordServers.update({ DiscordServers.id eq serverRow[DiscordServers.id] }) {
                    it[botJoined] = false
                    it[updatedAt] = now
                }
                gId
            } else {
                null
            }
        }
    }

    override fun softDelete(idOrGuildId: String): String? {
        val now = Instant.now()
        return transaction(database) {
            val serverRow = try {
                val uuid = UUID.fromString(idOrGuildId)
                DiscordServers.selectAll()
                    .where { (DiscordServers.id eq uuid) and DiscordServers.deletedAt.isNull() }
                    .singleOrNull()
            } catch (e: IllegalArgumentException) {
                DiscordServers.selectAll()
                    .where { (DiscordServers.guildId eq idOrGuildId) and DiscordServers.deletedAt.isNull() }
                    .singleOrNull()
            }

            if (serverRow != null) {
                val gId = serverRow[DiscordServers.guildId]
                DiscordServers.update({ DiscordServers.id eq serverRow[DiscordServers.id] }) {
                    it[deletedAt] = now
                    it[botJoined] = false
                    it[updatedAt] = now
                }
                gId
            } else {
                null
            }
        }
    }

    override fun upsertSyncedGuild(req: SyncGuildRequest) {
        val now = Instant.now()
        transaction(database) {
            val existing = DiscordServers.selectAll()
                .where { DiscordServers.guildId eq req.guildId }
                .singleOrNull()

            if (existing != null) {
                DiscordServers.update({ DiscordServers.guildId eq req.guildId }) {
                    it[name] = req.name
                    it[iconUrl] = req.iconUrl
                    it[ownerId] = req.ownerId
                    it[memberCount] = req.memberCount
                    it[botJoined] = req.botJoined
                    it[updatedAt] = now
                }
            } else {
                DiscordServers.insert {
                    it[guildId] = req.guildId
                    it[name] = req.name
                    it[iconUrl] = req.iconUrl
                    it[ownerId] = req.ownerId
                    it[memberCount] = req.memberCount
                    it[botJoined] = req.botJoined
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }
}

