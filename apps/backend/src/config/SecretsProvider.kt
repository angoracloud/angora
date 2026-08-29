package cloud.angora.config

import java.net.http.HttpClient

/**
 * Read-only view over the resolved secrets for this service.
 *
 * Implementations resolve a name the same way regardless of where the value came
 * from, so callers never branch on whether Infisical is in use.
 */
interface SecretsProvider {
    fun get(name: String): String?

    fun get(name: String, default: String): String = get(name) ?: default
}

/** What the backend did before Infisical existed. */
class EnvSecretsProvider : SecretsProvider {
    override fun get(name: String): String? = System.getenv(name)
}

/**
 * Resolves this service's secrets once, at startup.
 *
 * With `INFISICAL_ENABLED` unset this is just `System.getenv`. With it set,
 * Infisical values win and the environment stays the fallback for names the
 * project doesn't define.
 *
 * Throws if Infisical is enabled but unreachable, which aborts startup. Falling
 * back to the environment instead would let the backend boot on the angora/angora
 * credentials in docker-compose.yml and look healthy.
 */
fun loadSecrets(
    env: SecretsProvider = EnvSecretsProvider(),
    httpClient: HttpClient = HttpClient.newHttpClient()
): SecretsProvider {
    if (!InfisicalClient.isEnabled(env)) return env

    val config = InfisicalClient.resolveConfig(env)
    return InfisicalSecretsProvider(InfisicalClient.fetchSecrets(config, httpClient), env)
}
