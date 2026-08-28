package cloud.angora.repository

import cloud.angora.DiscordServers
import cloud.angora.dto.SyncGuildRequest
import cloud.angora.testsupport.PostgresRepositoryTest
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordRepositoryImplTest : PostgresRepositoryTest() {

    private val repository = DiscordRepositoryImpl(database)

    @AfterEach
    fun cleanup() {
        transaction(database) { DiscordServers.deleteAll() }
    }

    @Test
    fun `upsertSyncedGuild inserts a new server, findable via findAll`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-1", name = "Guild One"))

        val all = repository.findAll()
        assertEquals(1, all.size)
        assertEquals("guild-1", all[0].guildId)
        assertEquals("Guild One", all[0].name)
        assertTrue(all[0].botJoined)
    }

    @Test
    fun `upsertSyncedGuild on an existing guildId updates rather than duplicates`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-1", name = "Guild One", memberCount = 10))
        repository.upsertSyncedGuild(
            SyncGuildRequest(guildId = "guild-1", name = "Guild One Renamed", memberCount = 20)
        )

        val all = repository.findAll()
        assertEquals(1, all.size)
        assertEquals("Guild One Renamed", all[0].name)
        assertEquals(20, all[0].memberCount)
    }

    @Test
    fun `markServerLeft by guildId sets botJoined false and returns the guildId`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-2", name = "Guild Two"))

        val result = repository.markServerLeft("guild-2")

        assertEquals("guild-2", result)
        assertFalse(repository.findAll().single().botJoined)
    }

    @Test
    fun `markServerLeft by internal id sets botJoined false`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-3", name = "Guild Three"))
        val id = repository.findAll().single().id

        val result = repository.markServerLeft(id)

        assertEquals("guild-3", result)
        assertFalse(repository.findAll().single().botJoined)
    }

    @Test
    fun `markServerLeft returns null for an unknown id or guildId`() {
        assertNull(repository.markServerLeft("does-not-exist"))
    }

    @Test
    fun `softDelete by guildId sets deletedAt and removes server from findAll`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-4", name = "Guild Four"))
        assertEquals(1, repository.findAll().size)

        val result = repository.softDelete("guild-4")

        assertEquals("guild-4", result)
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `softDelete by internal id sets deletedAt and removes server from findAll`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-5", name = "Guild Five"))
        val id = repository.findAll().single().id

        val result = repository.softDelete(id)

        assertEquals("guild-5", result)
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `softDelete returns null for an unknown id or guildId`() {
        assertNull(repository.softDelete("does-not-exist"))
    }

    @Test
    fun `upsertSyncedGuild restores a soft-deleted server`() {
        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-6", name = "Guild Six"))
        repository.softDelete("guild-6")
        assertTrue(repository.findAll().isEmpty())

        repository.upsertSyncedGuild(SyncGuildRequest(guildId = "guild-6", name = "Guild Six Restored"))
        val all = repository.findAll()
        assertEquals(1, all.size)
        assertEquals("Guild Six Restored", all[0].name)
    }
}
