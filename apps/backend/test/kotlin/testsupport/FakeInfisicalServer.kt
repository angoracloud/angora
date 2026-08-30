package cloud.angora.testsupport

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * A real HTTP server standing in for Infisical, so the client is exercised over an
 * actual socket instead of a mocked `HttpClient`. Uses the JDK's own
 * `com.sun.net.httpserver`, so it adds no test dependency.
 */
class FakeInfisicalServer(
    loginPath: String,
    secretsPath: String
) : AutoCloseable {

    data class RecordedRequest(
        val method: String,
        val path: String,
        val query: String?,
        val authorization: String?,
        val body: String
    )

    private val server: HttpServer = HttpServer.create(InetSocketAddress(LOOPBACK, ANY_PORT), 0)

    val requests: MutableList<RecordedRequest> = mutableListOf()

    var loginStatus: Int = 200
    var loginBody: String = """{"accessToken":"$ISSUED_TOKEN"}"""
    var secretsStatus: Int = 200
    var secretsBody: String = """{"secrets":[]}"""

    val domain: String get() = "http://$LOOPBACK:${server.address.port}"

    init {
        server.createContext(loginPath) { exchange ->
            record(exchange)
            respond(exchange, loginStatus, loginBody)
        }
        server.createContext(secretsPath) { exchange ->
            record(exchange)
            respond(exchange, secretsStatus, secretsBody)
        }
        server.start()
    }

    /** Sets the secrets response to exactly these name/value pairs. */
    fun serveSecrets(vararg secrets: Pair<String, String>) {
        secretsBody = secrets.joinToString(
            separator = ",",
            prefix = """{"secrets":[""",
            postfix = "]}"
        ) { (key, value) -> """{"secretKey":"$key","secretValue":"$value"}""" }
    }

    fun requestFor(path: String): RecordedRequest? = requests.firstOrNull { it.path == path }

    private fun record(exchange: HttpExchange) {
        requests.add(
            RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                query = exchange.requestURI.query,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            )
        )
    }

    private fun respond(exchange: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        const val ISSUED_TOKEN = "issued-token"
        private const val LOOPBACK = "127.0.0.1"
        private const val ANY_PORT = 0
    }
}
