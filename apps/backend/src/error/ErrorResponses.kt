package cloud.angora.error

import cloud.angora.dto.ApiError
import cloud.angora.dto.ApiErrorEnvelope
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond

/**
 * Responds with the standard error envelope, stamped with this call's request id.
 *
 * Every error response in the API goes through here, so the shape stays identical
 * whether it came from StatusPages, an authentication challenge, or a route.
 */
suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String
) {
    respond(status, ApiErrorEnvelope(ApiError(code, message, callId ?: UNKNOWN_REQUEST_ID)))
}

private const val UNKNOWN_REQUEST_ID = "unknown"
