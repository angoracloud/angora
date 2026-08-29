package cloud.angora.config

import java.net.http.HttpClient

/**
 * Read-only view over the resolved secrets. Callers never branch on whether
 * Infisical is in use.
 */
interface SecretsProvider {
    fun get(name: String): String?

    fun get(name: String, default: String): String = get(name) ?: default
}

/** The first of [names] this provider defines, for values with more than one spelling. */
fun SecretsProvider.firstOf(vararg names: String): String? =
    names.firstNotNullOfOrNull { get(it) }

class EnvSecretsProvider : SecretsProvider {
    override fun get(name: String): String? = System.getenv(name)
}

/**
 * Resolves this service's secrets once, at startup: Infisical when enabled,
 * otherwise the environment.
 *
 * Throws if Infisical is enabled but unreachable; falling back would boot the
 * backend on the dev credentials in `docker-compose.yml`.
 */
fun loadSecrets(
    env: SecretsProvider = EnvSecretsProvider(),
    httpClient: HttpClient = HttpClient.newHttpClient()
): SecretsProvider {
    if (!InfisicalClient.isEnabled(env)) return env

    val config = InfisicalClient.resolveConfig(env)
    return InfisicalSecretsProvider(InfisicalClient.fetchSecrets(config, httpClient), env)
}
