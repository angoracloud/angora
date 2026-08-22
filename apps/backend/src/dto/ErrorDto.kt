package cloud.angora.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String
)

@Serializable
data class ApiErrorEnvelope(
    val error: ApiError
)
