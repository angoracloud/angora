package cloud.angora.routes

import cloud.angora.constants.BackendConstants
import cloud.angora.dto.SyncGuildRequest
import cloud.angora.dto.SyncStatusResponse
import cloud.angora.error.ApiException
import cloud.angora.service.DiscordService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.discordRoutes(discordService: DiscordService) {
    route(BackendConstants.Routes.DISCORD_BASE) {

        // CRM-facing routes: a signed-in human. Which roles may reach them is
        // follow-on work; for now any authenticated user qualifies.
        authenticate(BackendConstants.Auth.USER_PROVIDER) {
            get(BackendConstants.Routes.DISCORD_SERVERS) {
                val servers = discordService.getAllServers()
                call.respond(servers)
            }

            post(BackendConstants.Routes.DISCORD_SERVERS_LEAVE) {
                val idParam = call.parameters["id"]
                if (idParam.isNullOrBlank()) {
                    throw ApiException(
                        HttpStatusCode.BadRequest,
                        BackendConstants.Errors.MISSING_SERVER_ID_CODE,
                        BackendConstants.Errors.MISSING_SERVER_ID_MESSAGE
                    )
                }

                val response = discordService.leaveServer(idParam)
                    ?: throw ApiException(
                        HttpStatusCode.NotFound,
                        BackendConstants.Errors.SERVER_NOT_FOUND_CODE,
                        BackendConstants.Errors.SERVER_NOT_FOUND_MESSAGE
                    )
                call.respond(HttpStatusCode.OK, response)
            }

            delete(BackendConstants.Routes.DISCORD_SERVERS_BY_ID) {
                val idParam = call.parameters["id"]
                if (idParam.isNullOrBlank()) {
                    throw ApiException(
                        HttpStatusCode.BadRequest,
                        BackendConstants.Errors.MISSING_SERVER_ID_CODE,
                        BackendConstants.Errors.MISSING_SERVER_ID_MESSAGE
                    )
                }

                val response = discordService.deleteServer(idParam)
                    ?: throw ApiException(
                        HttpStatusCode.NotFound,
                        BackendConstants.Errors.SERVER_NOT_FOUND_CODE,
                        BackendConstants.Errors.SERVER_NOT_FOUND_MESSAGE
                    )
                call.respond(HttpStatusCode.OK, response)
            }

            get(BackendConstants.Routes.DISCORD_BOT_INVITE) {
                val inviteInfo = discordService.getInviteInfo()
                call.respond(inviteInfo)
            }
        }

        // Machine-facing: the bot reporting guild state, with a service token.
        authenticate(BackendConstants.Auth.SERVICE_PROVIDER) {
            post(BackendConstants.Routes.DISCORD_BOT_SYNC) {
                val req = call.receive<SyncGuildRequest>()
                discordService.syncGuild(req)
                call.respond(HttpStatusCode.OK, SyncStatusResponse(status = "synced"))
            }
        }
    }
}

