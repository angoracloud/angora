package cloud.angora.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import org.slf4j.event.Level
import java.util.UUID

/**
 * Request-id propagation and access logging.
 *
 * Accepts an inbound `X-Request-Id` or generates one, then puts it in SLF4J's MDC
 * under `requestId` for the duration of the call — which is what lets
 * `logback.xml`'s `%X{requestId}` tag every line from every layer with the request
 * that caused it. The same id goes into every error envelope.
 */
fun Application.configureMonitoring() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { it.isNotBlank() }
        generate { UUID.randomUUID().toString() }
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
    }
}
