package cloud.angora.plugins

import cloud.angora.constants.BackendConstants
import cloud.angora.error.ApiException
import cloud.angora.error.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri

/**
 * Maps everything that can go wrong onto the single error envelope defined in
 * `dto/ErrorDto.kt`, so no response in the API is shaped differently.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        // The convention for expected 4xx/5xx: routes and services throw this
        // rather than building a response by hand.
        exception<ApiException> { call, cause ->
            call.respondError(cause.statusCode, cause.code, cause.message)
        }

        // A body that is absent, unparseable, or sent without a usable
        // Content-Type. Ktor raises two unrelated types for these
        // (ContentTransformationException's subclass UnsupportedMediaTypeException
        // covers the header case), and both need handling — otherwise they reach
        // the Throwable branch and get reported as a 500, blaming the server for a
        // client mistake.
        exception<BadRequestException> { call, cause -> call.respondMalformed(cause) }
        exception<ContentTransformationException> { call, cause -> call.respondMalformed(cause) }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception processing ${call.request.uri}", cause)
            call.respondError(
                HttpStatusCode.InternalServerError,
                BackendConstants.Errors.INTERNAL_ERROR_CODE,
                BackendConstants.Errors.INTERNAL_ERROR_MESSAGE
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondError(
                HttpStatusCode.NotFound,
                BackendConstants.Errors.NOT_FOUND_CODE,
                BackendConstants.Errors.NOT_FOUND_MESSAGE
            )
        }

        // RateLimit rejects before any handler runs, so without this a throttled
        // request would be the one response in the API with no envelope.
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            call.respondError(
                HttpStatusCode.TooManyRequests,
                BackendConstants.Errors.RATE_LIMITED_CODE,
                BackendConstants.Errors.RATE_LIMITED_MESSAGE
            )
        }
    }
}

private suspend fun ApplicationCall.respondMalformed(cause: Throwable) {
    application.log.info("Rejected malformed request to ${request.uri}: ${cause.message}")
    respondError(
        HttpStatusCode.BadRequest,
        BackendConstants.Errors.INVALID_REQUEST_BODY_CODE,
        BackendConstants.Errors.INVALID_REQUEST_BODY_MESSAGE
    )
}
