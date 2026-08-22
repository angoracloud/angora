package cloud.angora.routes

import cloud.angora.constants.BackendConstants
import cloud.angora.service.HealthService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes(healthService: HealthService) {
    get(BackendConstants.Routes.HEALTH_PATH) {
        val healthStatus = healthService.getHealthStatus()
        val httpStatus = if (healthStatus.status == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(httpStatus, healthStatus)
    }
}

