package cloud.angora.repository

import cloud.angora.ServiceTokens
import cloud.angora.constants.BackendConstants
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import cloud.angora.testsupport.PostgresRepositoryTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ServiceTokenRepositoryImplTest : PostgresRepositoryTest() {

    private val repository = ServiceTokenRepositoryImpl(database)

    @AfterEach
    fun cleanup() {
        transaction(database) { ServiceTokens.deleteAll() }
    }

    @Test
    fun `upsertByName creates a token findable by its hash`() {
        repository.upsertByName(BackendConstants.Auth.DISCORD_BOT_TOKEN_NAME, "hash-1")

        val found = repository.findActiveByHash("hash-1")

        assertNotNull(found)
        assertEquals(BackendConstants.Auth.DISCORD_BOT_TOKEN_NAME, found?.name)
    }

    @Test
    fun `upsertByName replaces the hash instead of adding a second row`() {
        repository.upsertByName("discord-bot", "hash-old")
        repository.upsertByName("discord-bot", "hash-new")

        // Rotating the configured token must retire the previous one, not leave
        // both working.
        assertNull(repository.findActiveByHash("hash-old"))
        assertNotNull(repository.findActiveByHash("hash-new"))
        assertEquals(1, transaction(database) { ServiceTokens.selectAll().count() })
    }

    @Test
    fun `does not resolve an unknown hash`() {
        repository.upsertByName("discord-bot", "hash-1")

        assertNull(repository.findActiveByHash("some-other-hash"))
    }

    @Test
    fun `does not resolve a revoked token`() {
        repository.upsertByName("discord-bot", "hash-1")
        transaction(database) {
            ServiceTokens.update({ ServiceTokens.name eq "discord-bot" }) {
                it[revokedAt] = Instant.now()
            }
        }

        assertNull(repository.findActiveByHash("hash-1"))
    }

    @Test
    fun `upsertByName clears a previous revocation`() {
        repository.upsertByName("discord-bot", "hash-1")
        transaction(database) {
            ServiceTokens.update({ ServiceTokens.name eq "discord-bot" }) {
                it[revokedAt] = Instant.now()
            }
        }

        // Re-registering at startup with a fresh token has to bring the row back
        // into service, otherwise a revoked name could never be reused.
        repository.upsertByName("discord-bot", "hash-2")

        assertNotNull(repository.findActiveByHash("hash-2"))
    }

    @Test
    fun `recordUsage records the timestamp`() {
        repository.upsertByName("discord-bot", "hash-1")
        val id = repository.findActiveByHash("hash-1")!!.id

        repository.recordUsage(id, Instant.now())

        val lastUsed = transaction(database) {
            ServiceTokens.selectAll().where { ServiceTokens.id eq id }.first()[ServiceTokens.lastUsedAt]
        }
        assertNotNull(lastUsed)
    }
}
