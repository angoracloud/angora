package cloud.angora.service

import cloud.angora.dto.HealthResponse
import cloud.angora.repository.HealthRepository

interface HealthService {
    fun getHealthStatus(): HealthResponse
}

class HealthServiceImpl(private val healthRepository: HealthRepository) : HealthService {
    override fun getHealthStatus(): HealthResponse {
        return try {
            val isConnected = healthRepository.checkDatabaseConnection()
            if (isConnected) {
                HealthResponse(status = "ok", database = "connected")
            } else {
                HealthResponse(status = "error", database = "disconnected", error = "Database check returned false")
            }
        } catch (e: Exception) {
            HealthResponse(status = "error", database = "disconnected", error = e.message)
        }
    }
}
