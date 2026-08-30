package cloud.angora.config

import cloud.angora.constants.BackendConstants
import cloud.angora.testsupport.FakeInfisicalServer
import cloud.angora.testsupport.MapSecretsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// The Assertions.assertThrows overload, not the reified `assertThrows<T>`: that
// one is inline and compiled for JVM 17, which the Kotlin plugin's default 1.8
// target refuses to inline. Same reason as service/AuthServiceImplTest.kt.
class LoadSecretsTest {

    private lateinit var server: FakeInfisicalServer

    @BeforeEach
    fun startServer() {
        server = FakeInfisicalServer(
            loginPath = BackendConstants.Infisical.UNIVERSAL_AUTH_LOGIN_PATH,
            secretsPath = BackendConstants.Infisical.LIST_SECRETS_PATH
        )
    }

    @AfterEach
    fun stopServer() {
        server.close()
    }

    private fun enabledEnv(vararg extra: Pair<String, String>): MapSecretsProvider =
        MapSecretsProvider(
            mapOf(
                BackendConstants.Infisical.ENABLED_ENV to BackendConstants.Infisical.ENABLED_VALUE,
                BackendConstants.Infisical.PROJECT_ID_ENV to PROJECT_ID,
                BackendConstants.Infisical.TOKEN_ENV to PRE_ISSUED_TOKEN,
                BackendConstants.Infisical.DOMAIN_ENV to server.domain
            ) + extra
        )

    @Test
    fun `returns the environment provider untouched when the flag is off`() {
        val env = MapSecretsProvider(mapOf("TOKEN" to "from-env"))

        val secrets = loadSecrets(env)

        assertSame(env, secrets)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `treats any value other than true as disabled`() {
        val env = MapSecretsProvider(
            mapOf(
                BackendConstants.Infisical.ENABLED_ENV to "1",
                "TOKEN" to "from-env"
            )
        )

        assertSame(env, loadSecrets(env))
    }

    @Test
    fun `prefers an Infisical value over the same name in the environment`() {
        server.serveSecrets("TOKEN" to "from-infisical")

        val secrets = loadSecrets(enabledEnv("TOKEN" to "from-env"))

        assertEquals("from-infisical", secrets.get("TOKEN"))
    }

    @Test
    fun `falls back to the environment for a name Infisical does not define`() {
        server.serveSecrets("TOKEN" to "from-infisical")

        val secrets = loadSecrets(enabledEnv("OTHER" to "only-in-env"))

        assertEquals("only-in-env", secrets.get("OTHER"))
        assertNull(secrets.get("ABSENT"))
        assertEquals("fallback", secrets.get("ABSENT", "fallback"))
    }

    @Test
    fun `skips the login call when a pre-issued token is configured`() {
        server.serveSecrets("TOKEN" to "from-infisical")

        loadSecrets(enabledEnv())

        assertNull(server.requestFor(BackendConstants.Infisical.UNIVERSAL_AUTH_LOGIN_PATH))
        assertEquals(
            "${BackendConstants.Infisical.BEARER_PREFIX}$PRE_ISSUED_TOKEN",
            server.requestFor(BackendConstants.Infisical.LIST_SECRETS_PATH)?.authorization
        )
    }

    @Test
    fun `logs in with client credentials and uses the returned access token`() {
        server.serveSecrets("TOKEN" to "from-infisical")
        val env = MapSecretsProvider(
            mapOf(
                BackendConstants.Infisical.ENABLED_ENV to BackendConstants.Infisical.ENABLED_VALUE,
                BackendConstants.Infisical.PROJECT_ID_ENV to PROJECT_ID,
                BackendConstants.Infisical.DOMAIN_ENV to server.domain,
                BackendConstants.Infisical.CLIENT_ID_ENV to "client-id",
                BackendConstants.Infisical.CLIENT_SECRET_ENV to "client-secret"
            )
        )

        val secrets = loadSecrets(env)

        assertEquals("from-infisical", secrets.get("TOKEN"))

        val login = server.requestFor(BackendConstants.Infisical.UNIVERSAL_AUTH_LOGIN_PATH)
        assertEquals("POST", login?.method)
        assertTrue(login?.body?.contains("client-secret") == true)

        assertEquals(
            "${BackendConstants.Infisical.BEARER_PREFIX}${FakeInfisicalServer.ISSUED_TOKEN}",
            server.requestFor(BackendConstants.Infisical.LIST_SECRETS_PATH)?.authorization
        )
    }

    @Test
    fun `targets the configured project, environment and path`() {
        val secretPath = "/backend"
        loadSecrets(
            enabledEnv(
                BackendConstants.Infisical.ENVIRONMENT_ENV to "prod",
                BackendConstants.Infisical.SECRET_PATH_ENV to secretPath
            )
        )

        val query = server.requestFor(BackendConstants.Infisical.LIST_SECRETS_PATH)?.query
        assertTrue(query?.contains("${BackendConstants.Infisical.PROJECT_ID_QUERY}=$PROJECT_ID") == true)
        assertTrue(query?.contains("${BackendConstants.Infisical.ENVIRONMENT_QUERY}=prod") == true)
        assertTrue(query?.contains(BackendConstants.Infisical.SECRET_PATH_QUERY) == true)
    }

    @Test
    fun `aborts startup when the project id is missing`() {
        val env = MapSecretsProvider(
            mapOf(BackendConstants.Infisical.ENABLED_ENV to BackendConstants.Infisical.ENABLED_VALUE)
        )

        val error = assertThrows(IllegalStateException::class.java) { loadSecrets(env) }

        assertTrue(error.message?.contains(BackendConstants.Infisical.PROJECT_ID_ENV) == true)
    }

    @Test
    fun `aborts startup when neither a token nor client credentials are configured`() {
        val env = MapSecretsProvider(
            mapOf(
                BackendConstants.Infisical.ENABLED_ENV to BackendConstants.Infisical.ENABLED_VALUE,
                BackendConstants.Infisical.PROJECT_ID_ENV to PROJECT_ID
            )
        )

        val error = assertThrows(IllegalStateException::class.java) { loadSecrets(env) }

        assertTrue(error.message?.contains(BackendConstants.Infisical.CLIENT_ID_ENV) == true)
    }

    @Test
    fun `aborts startup rather than serving environment values when the fetch fails`() {
        server.secretsStatus = 403
        server.secretsBody = """{"message":"forbidden"}"""

        val error = assertThrows(IllegalStateException::class.java) {
            loadSecrets(enabledEnv("TOKEN" to "from-env"))
        }

        assertTrue(error.message?.contains("403") == true)
    }

    @Test
    fun `names the domain when Infisical cannot be reached at all`() {
        // Port 1 on loopback: nothing listens, so the connection is refused.
        val unreachable = "http://127.0.0.1:1"
        val env = MapSecretsProvider(
            mapOf(
                BackendConstants.Infisical.ENABLED_ENV to BackendConstants.Infisical.ENABLED_VALUE,
                BackendConstants.Infisical.PROJECT_ID_ENV to PROJECT_ID,
                BackendConstants.Infisical.TOKEN_ENV to PRE_ISSUED_TOKEN,
                BackendConstants.Infisical.DOMAIN_ENV to unreachable
            )
        )

        val error = assertThrows(IllegalStateException::class.java) {
            loadSecrets(env)
        }

        assertEquals(
            BackendConstants.Infisical.Failures.UNREACHABLE.format(unreachable),
            error.message
        )
    }

    @Test
    fun `aborts startup when the login response carries no access token`() {
        val env = MapSecretsProvider(
            mapOf(
                BackendConstants.Infisical.ENABLED_ENV to BackendConstants.Infisical.ENABLED_VALUE,
                BackendConstants.Infisical.PROJECT_ID_ENV to PROJECT_ID,
                BackendConstants.Infisical.DOMAIN_ENV to server.domain,
                BackendConstants.Infisical.CLIENT_ID_ENV to "client-id",
                BackendConstants.Infisical.CLIENT_SECRET_ENV to "client-secret"
            )
        )
        server.loginBody = """{"unexpected":true}"""

        val error = assertThrows(IllegalStateException::class.java) { loadSecrets(env) }

        assertEquals(
            BackendConstants.Infisical.Failures.MALFORMED_LOGIN_RESPONSE,
            error.message
        )
    }

    private companion object {
        const val PROJECT_ID = "proj-123"
        const val PRE_ISSUED_TOKEN = "pre-issued-token"
    }
}
