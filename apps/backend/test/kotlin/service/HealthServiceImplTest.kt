package cloud.angora.service

import cloud.angora.repository.HealthRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private class FakeHealthRepository(
    private val connected: Boolean = true,
    private val throwable: Exception? = null
) : HealthRepository {
    override fun checkDatabaseConnection(): Boolean {
        throwable?.let { throw it }
        return connected
    }
}

class HealthServiceImplTest {

    @Test
    fun `reports ok status when database is connected`() {
        val service = HealthServiceImpl(FakeHealthRepository(connected = true))

        val result = service.getHealthStatus()

        assertEquals("ok", result.status)
        assertEquals("connected", result.database)
    }

    @Test
    fun `reports error status when database check returns false`() {
        val service = HealthServiceImpl(FakeHealthRepository(connected = false))

        val result = service.getHealthStatus()

        assertEquals("error", result.status)
        assertEquals("disconnected", result.database)
    }

    @Test
    fun `reports error status when database check throws`() {
        val service = HealthServiceImpl(FakeHealthRepository(throwable = RuntimeException("connection refused")))

        val result = service.getHealthStatus()

        assertEquals("error", result.status)
        assertEquals("disconnected", result.database)
        assertEquals("connection refused", result.error)
    }
}
