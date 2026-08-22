package cloud.angora.error

import io.ktor.http.HttpStatusCode

class ApiException(
    val statusCode: HttpStatusCode,
    val code: String,
    override val message: String
) : RuntimeException(message)
