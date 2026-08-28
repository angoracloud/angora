package cloud.angora.plugins

import cloud.angora.validation.configureRequestValidation
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation

/**
 * Request validation plugin configuration.
 */
fun Application.configureValidation() {
    install(RequestValidation) {
        configureRequestValidation()
    }
}
