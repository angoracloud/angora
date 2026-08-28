package cloud.angora.routes

import cloud.angora.auth.ServicePrincipal
import cloud.angora.constants.BackendConstants
import cloud.angora.dto.*
import cloud.angora.plugins.configureErrorHandling
import cloud.angora.plugins.configureValidation
import cloud.angora.service.DiscordService
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

private class FakeDiscordService : DiscordService {
    val syncedGuilds = mutableListOf<SyncGuildRequest>()

    override fun getAllServers(): List<DiscordServerDto> = emptyList()

    override fun leaveServer(idOrGuildId: String): DeleteServerResponse? {
        return if (idOrGuildId == "found") {
            DeleteServerResponse(
                status = BackendConstants.Discord.ServerStatus.UPDATED,
                guildId = "found",
                botJoined = false
            )
        } else {
            null
        }
    }

    override fun deleteServer(idOrGuildId: String): DeleteServerResponse? {
        return if (idOrGuildId == "found") {
            DeleteServerResponse(
                status = BackendConstants.Discord.ServerStatus.DELETED,
                guildId = "found",
                botJoined = false
            )
        } else {
            null
        }
    }

    override fun syncGuild(req: SyncGuildRequest) {
        syncedGuilds.add(req)
    }

    override fun getInviteInfo(): DiscordInviteResponse {
        return DiscordInviteResponse(inviteUrl = "https://discord.com/oauth", clientId = "123")
    }
}

class DiscordRoutesValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val validServiceToken = "test-service-token"

    private fun ApplicationTestBuilder.configureTestApp(discordService: DiscordService) {
        application {
            install(CallId) {
                header(HttpHeaders.XRequestId)
                generate { UUID.randomUUID().toString() }
            }
            configureErrorHandling()
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(Authentication) {
                bearer(BackendConstants.Auth.SERVICE_PROVIDER) {
                    authenticate { credential ->
                        if (credential.token == validServiceToken) {
                            ServicePrincipal(UUID.randomUUID(), "discord-bot")
                        } else {
                            null
                        }
                    }
                }
                bearer(BackendConstants.Auth.USER_PROVIDER) {
                    authenticate { null }
                }
            }
            configureValidation()
            routing {
                discordRoutes(discordService)
            }
        }
    }

    @Test
    fun `POST sync valid guild succeeds with 200`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "guildId": "123456789012345678",
                    "name": "General Angora Server",
                    "memberCount": 15,
                    "botJoined": true
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, fakeService.syncedGuilds.size)
        assertEquals("123456789012345678", fakeService.syncedGuilds[0].guildId)
    }

    @Test
    fun `POST sync without authorization token returns 401 Unauthorized`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "guildId": "123456789012345678",
                    "name": "General Angora Server",
                    "memberCount": 15
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, fakeService.syncedGuilds.size)
    }

    @Test
    fun `POST sync rejects blank guildId with 400 validation error`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "guildId": "",
                    "name": "General Angora Server",
                    "memberCount": 15
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
        assertEquals(BackendConstants.Errors.VALIDATION_ERROR_CODE, envelope.error.code)
        assertTrue(envelope.error.message.contains("guildId must not be blank"))
        assertEquals(0, fakeService.syncedGuilds.size)
    }

    @Test
    fun `POST sync rejects negative memberCount with 400 validation error`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "guildId": "123456",
                    "name": "General Angora Server",
                    "memberCount": -1
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
        assertEquals(BackendConstants.Errors.VALIDATION_ERROR_CODE, envelope.error.code)
        assertTrue(envelope.error.message.contains("memberCount must be zero or positive"))
    }

    @Test
    fun `POST sync rejects malformed JSON with 400 invalid_request_body error`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            contentType(ContentType.Application.Json)
            setBody("{ malformed json }")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
        assertEquals(BackendConstants.Errors.INVALID_REQUEST_BODY_CODE, envelope.error.code)
        assertEquals(BackendConstants.Errors.INVALID_REQUEST_BODY_MESSAGE, envelope.error.message)
    }

    @Test
    fun `POST sync rejects missing or non-json Content-Type with 400 invalid_request_body error`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            setBody("not-json-content")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
        assertEquals(BackendConstants.Errors.INVALID_REQUEST_BODY_CODE, envelope.error.code)
        assertEquals(BackendConstants.Errors.INVALID_REQUEST_BODY_MESSAGE, envelope.error.message)
    }
}
