package cloud.angora.config

import cloud.angora.constants.BackendConstants
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/** Everything needed to talk to one Infisical project/environment/path. */
data class InfisicalConfig(
    val domain: String,
    val projectId: String,
    val environment: String,
    val secretPath: String,
    val clientId: String?,
    val clientSecret: String?,
    val token: String?
)

/**
 * Serves secrets fetched from Infisical at startup, falling back to [fallback]
 * — the environment — for any name the project doesn't define.
 *
 * The map is captured once. Rotating a secret requires a restart.
 */
class InfisicalSecretsProvider(
    secrets: Map<String, String>,
    private val fallback: SecretsProvider
) : SecretsProvider {

    private val secrets: Map<String, String> = secrets.toMap()

    override fun get(name: String): String? = secrets[name] ?: fallback.get(name)
}

@Serializable
private data class UniversalAuthLoginRequest(
    val clientId: String,
    val clientSecret: String
)

@Serializable
private data class UniversalAuthLoginResponse(
    val accessToken: String? = null
)

@Serializable
private data class InfisicalSecret(
    val secretKey: String? = null,
    val secretValue: String? = null
)

@Serializable
private data class ListSecretsResponse(
    val secrets: List<InfisicalSecret> = emptyList()
)

/**
 * Talks to Infisical's REST API over the JDK's own HTTP client.
 *
 * Not the official `com.infisical:sdk`: it carries `spring-boot-starter-parent`
 * as its parent POM plus okhttp 2.7.5 and logback 1.3.14 (this module pins
 * 1.5.38), all of which would end up shaded into `backend.jar`. Two REST calls
 * were cheaper than that dependency tree.
 */
object InfisicalClient {

    private val json = Json { ignoreUnknownKeys = true }

    fun isEnabled(env: SecretsProvider): Boolean =
        env.get(BackendConstants.Infisical.ENABLED_ENV) == BackendConstants.Infisical.ENABLED_VALUE

    /**
     * Builds the connection config from the environment, failing loudly on
     * anything missing. Only reached when [isEnabled] is true, so an incomplete
     * configuration is an operator error rather than a reason to fall back.
     */
    fun resolveConfig(env: SecretsProvider): InfisicalConfig {
        val projectId = env.trimmed(BackendConstants.Infisical.PROJECT_ID_ENV)
            ?: error(BackendConstants.Infisical.Failures.MISSING_PROJECT_ID)

        val token = env.trimmed(BackendConstants.Infisical.TOKEN_ENV)
        val clientId = env.trimmed(BackendConstants.Infisical.CLIENT_ID_ENV)
        val clientSecret = env.trimmed(BackendConstants.Infisical.CLIENT_SECRET_ENV)

        if (token == null && (clientId == null || clientSecret == null)) {
            error(BackendConstants.Infisical.Failures.MISSING_CREDENTIALS)
        }

        return InfisicalConfig(
            domain = (env.trimmed(BackendConstants.Infisical.DOMAIN_ENV)
                ?: BackendConstants.Infisical.DEFAULT_DOMAIN).trimEnd('/'),
            projectId = projectId,
            environment = env.trimmed(BackendConstants.Infisical.ENVIRONMENT_ENV)
                ?: BackendConstants.Infisical.DEFAULT_ENVIRONMENT,
            secretPath = env.trimmed(BackendConstants.Infisical.SECRET_PATH_ENV)
                ?: BackendConstants.Infisical.DEFAULT_SECRET_PATH,
            clientId = clientId,
            clientSecret = clientSecret,
            token = token
        )
    }

    /**
     * Fetches every secret at the configured project/environment/path.
     *
     * Throws on any failure; the caller is expected to let that abort startup.
     */
    fun fetchSecrets(
        config: InfisicalConfig,
        httpClient: HttpClient = HttpClient.newHttpClient()
    ): Map<String, String> {
        val accessToken = config.token ?: login(config, httpClient)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(listSecretsUrl(config)))
            .timeout(BackendConstants.Infisical.REQUEST_TIMEOUT)
            .header(
                HttpHeaders.Authorization,
                "${BackendConstants.Infisical.BEARER_PREFIX}$accessToken"
            )
            .GET()
            .build()

        val response = send(request, httpClient, config.domain)
        if (response.statusCode() !in BackendConstants.Infisical.SUCCESS_STATUS_RANGE) {
            error("${BackendConstants.Infisical.Failures.FETCH_FAILED} (${response.statusCode()})")
        }

        return json.decodeFromString<ListSecretsResponse>(response.body())
            .secrets
            .mapNotNull { secret ->
                val key = secret.secretKey
                val value = secret.secretValue
                if (key != null && value != null) key to value else null
            }
            .toMap()
    }

    /**
     * Exchanges a machine identity's client credentials for a short-lived access
     * token. Skipped when a pre-issued token is configured.
     */
    private fun login(config: InfisicalConfig, httpClient: HttpClient): String {
        val body = json.encodeToString(
            UniversalAuthLoginRequest(
                clientId = requireNotNull(config.clientId),
                clientSecret = requireNotNull(config.clientSecret)
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("${config.domain}${BackendConstants.Infisical.UNIVERSAL_AUTH_LOGIN_PATH}"))
            .timeout(BackendConstants.Infisical.REQUEST_TIMEOUT)
            .header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = send(request, httpClient, config.domain)
        if (response.statusCode() !in BackendConstants.Infisical.SUCCESS_STATUS_RANGE) {
            error("${BackendConstants.Infisical.Failures.LOGIN_FAILED} (${response.statusCode()})")
        }

        return json.decodeFromString<UniversalAuthLoginResponse>(response.body())
            .accessToken
            ?.takeIf { it.isNotEmpty() }
            ?: error(BackendConstants.Infisical.Failures.MALFORMED_LOGIN_RESPONSE)
    }

    /**
     * Sends the request, translating a transport failure into a message that
     * names the domain. Without this the operator gets a bare `ConnectException`
     * stack trace that never mentions Infisical or where it tried to connect.
     */
    private fun send(
        request: HttpRequest,
        httpClient: HttpClient,
        domain: String
    ): HttpResponse<String> =
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw IllegalStateException(
                BackendConstants.Infisical.Failures.UNREACHABLE.format(domain),
                e
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException(
                BackendConstants.Infisical.Failures.UNREACHABLE.format(domain),
                e
            )
        }

    private fun listSecretsUrl(config: InfisicalConfig): String {
        val query = listOf(
            BackendConstants.Infisical.PROJECT_ID_QUERY to config.projectId,
            BackendConstants.Infisical.ENVIRONMENT_QUERY to config.environment,
            BackendConstants.Infisical.SECRET_PATH_QUERY to config.secretPath
        ).joinToString("&") { (name, value) -> "$name=${value.urlEncoded()}" }

        return "${config.domain}${BackendConstants.Infisical.LIST_SECRETS_PATH}?$query"
    }

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private fun SecretsProvider.trimmed(name: String): String? =
        get(name)?.trim()?.takeIf { it.isNotEmpty() }
}
