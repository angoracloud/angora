package cloud.angora.testsupport

import cloud.angora.config.SecretsProvider

/** In-memory stand-in for the process environment. */
class MapSecretsProvider(
    private val values: Map<String, String>
) : SecretsProvider {
    override fun get(name: String): String? = values[name]
}
