package cloud.angora.plugins

import cloud.angora.constants.BackendConstants
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * JSON serialization and CORS.
 *
 * Note there is no `anyHost()`: the session cookie makes every request
 * credentialed, and Ktor refuses to pair a wildcard origin with
 * `allowCredentials`. The allowed list is normally empty, since nginx serves the
 * frontend from the same origin as the API — it exists for `pnpm run dev:frontend`,
 * where Vite serves on another port.
 */
fun Application.configureHttp() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            encodeDefaults = true
        })
    }

    install(CORS) {
        allowedOrigins().forEach { origin ->
            allowHost(
                host = origin.host + if (origin.port != -1) ":${origin.port}" else "",
                schemes = listOf(origin.scheme)
            )
        }
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowNonSimpleContentTypes = true
        allowCredentials = true
    }
}

private fun allowedOrigins(): List<URI> =
    (System.getenv(BackendConstants.Auth.CORS_ALLOWED_ORIGINS_ENV) ?: "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { URI(it) }
