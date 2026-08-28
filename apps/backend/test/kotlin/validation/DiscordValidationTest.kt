package cloud.angora.validation

import cloud.angora.dto.SyncGuildRequest
import io.ktor.server.plugins.requestvalidation.ValidationResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscordValidationTest {

    @Test
    fun `validateSyncGuildRequest accepts valid request`() {
        val req = SyncGuildRequest(
            guildId = "123456789012345678",
            name = "Test Discord Server",
            iconUrl = "https://cdn.discordapp.com/icons/123/abc.png",
            ownerId = "987654321098765432",
            memberCount = 42,
            botJoined = true
        )

        val result = validateSyncGuildRequest(req)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateSyncGuildRequest rejects blank guildId`() {
        val req = SyncGuildRequest(
            guildId = "   ",
            name = "Test Server"
        )

        val result = validateSyncGuildRequest(req)
        assertTrue(result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue(invalid.reasons.any { it.contains("guildId must not be blank") })
    }

    @Test
    fun `validateSyncGuildRequest rejects blank name`() {
        val req = SyncGuildRequest(
            guildId = "123456",
            name = ""
        )

        val result = validateSyncGuildRequest(req)
        assertTrue(result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue(invalid.reasons.any { it.contains("name must not be blank") })
    }

    @Test
    fun `validateSyncGuildRequest rejects negative member count`() {
        val req = SyncGuildRequest(
            guildId = "123456",
            name = "Test Server",
            memberCount = -5
        )

        val result = validateSyncGuildRequest(req)
        assertTrue(result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue(invalid.reasons.any { it.contains("memberCount must be zero or positive") })
    }

    @Test
    fun `validateSyncGuildRequest rejects invalid iconUrl`() {
        val req = SyncGuildRequest(
            guildId = "123456",
            name = "Test Server",
            iconUrl = "invalid-url-format"
        )

        val result = validateSyncGuildRequest(req)
        assertTrue(result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertTrue(invalid.reasons.any { it.contains("iconUrl must be a valid http or https URL") })
    }

    @Test
    fun `validateSyncGuildRequest collects multiple validation errors`() {
        val req = SyncGuildRequest(
            guildId = "",
            name = "",
            memberCount = -10,
            iconUrl = "ftp://not-http.com"
        )

        val result = validateSyncGuildRequest(req)
        assertTrue(result is ValidationResult.Invalid)
        val invalid = result as ValidationResult.Invalid
        assertEquals(4, invalid.reasons.size)
    }
}
