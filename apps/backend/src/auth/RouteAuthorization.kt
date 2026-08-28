package cloud.angora.auth

import cloud.angora.constants.BackendConstants
import cloud.angora.error.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/**
 * The authenticated user behind this call.
 *
 * @throws ApiException 401 when there is none — unreachable inside an
 *   `authenticate` block, and a wiring bug rather than a client error if it fires.
 */
fun ApplicationCall.requireUser(): UserPrincipal =
    principal<UserPrincipal>() ?: throw ApiException(
        HttpStatusCode.Unauthorized,
        BackendConstants.Errors.UNAUTHENTICATED_CODE,
        BackendConstants.Errors.UNAUTHENTICATED_MESSAGE
    )
