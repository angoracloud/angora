package cloud.angora.routes

import cloud.angora.auth.ServicePrincipal
import cloud.angora.constants.BackendConstants
import cloud.angora.dto.*
import cloud.angora.error.ApiException
import cloud.angora.service.DiscordService
import cloud.angora.validation.configureRequestValidation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

private class FakeDiscordService : DiscordService {
    val syncedGuilds = mutableListOf<SyncGuildRequest>()

    override fun getAllServers(): List<DiscordServerDto> = emptyList()

    override fun leaveServer(idOrGuildId: String): DeleteServerResponse? {
        return if (idOrGuildId == "found") {
            DeleteServerResponse(status = "updated", guildId = "found", botJoined = false)
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
            install(StatusPages) {
                exception<ApiException> { call, cause ->
                    call.respond(
                        cause.statusCode,
                        ApiErrorEnvelope(ApiError(cause.code, cause.message, call.callId ?: "unknown"))
                    )
                }
                exception<RequestValidationException> { call, cause ->
                    val message = if (cause.reasons.isNotEmpty()) {
                        cause.reasons.joinToString("; ")
                    } else {
                        BackendConstants.Errors.VALIDATION_ERROR_MESSAGE
                    }
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorEnvelope(
                            ApiError(
                                BackendConstants.Errors.VALIDATION_ERROR_CODE,
                                message,
                                call.callId ?: "unknown"
                            )
                        )
                    )
                }
                exception<BadRequestException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorEnvelope(
                            ApiError(
                                BackendConstants.Errors.BAD_REQUEST_CODE,
                                cause.message?.substringBefore("\n") ?: BackendConstants.Errors.BAD_REQUEST_MESSAGE,
                                call.callId ?: "unknown"
                            )
                        )
                    )
                }
                exception<SerializationException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorEnvelope(
                            ApiError(
                                BackendConstants.Errors.INVALID_JSON_CODE,
                                BackendConstants.Errors.INVALID_JSON_MESSAGE,
                                call.callId ?: "unknown"
                            )
                        )
                    )
                }
                exception<CannotTransformContentToTypeException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiErrorEnvelope(
                            ApiError(
                                BackendConstants.Errors.INVALID_JSON_CODE,
                                BackendConstants.Errors.INVALID_JSON_MESSAGE,
                                call.callId ?: "unknown"
                            )
                        )
                    )
                }
                exception<Throwable> { call, cause ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiErrorEnvelope(
                            ApiError(
                                BackendConstants.Errors.INTERNAL_ERROR_CODE,
                                BackendConstants.Errors.INTERNAL_ERROR_MESSAGE,
                                call.callId ?: "unknown"
                            )
                        )
                    )
                }
            }
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
            install(RequestValidation) {
                configureRequestValidation()
            }
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
        assertEquals("validation_error", envelope.error.code)
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
        assertEquals("validation_error", envelope.error.code)
        assertTrue(envelope.error.message.contains("memberCount must be zero or positive"))
    }

    @Test
    fun `POST sync rejects malformed JSON with 400 bad request error`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            contentType(ContentType.Application.Json)
            setBody("{ malformed json }")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
        assertTrue(envelope.error.code == "bad_request" || envelope.error.code == "invalid_json")
    }

    @Test
    fun `POST sync rejects missing or non-json Content-Type with 400 bad request error`() = testApplication {
        val fakeService = FakeDiscordService()
        configureTestApp(fakeService)

        val response = client.post("/api/discord/bot/sync") {
            header(HttpHeaders.Authorization, "Bearer $validServiceToken")
            setBody("not-json-content")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val envelope = json.decodeFromString<ApiErrorEnvelope>(response.bodyAsText())
        assertEquals("invalid_json", envelope.error.code)
    }
}
