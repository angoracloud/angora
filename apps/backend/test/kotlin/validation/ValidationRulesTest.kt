package cloud.angora.validation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ValidationRulesTest {

    @Test
    fun `requireNonBlank accepts valid non-empty string`() {
        assertNull(ValidationRules.requireNonBlank("valid-value", "field"))
    }

    @Test
    fun `requireNonBlank rejects null, empty, and whitespace-only strings`() {
        assertNotNull(ValidationRules.requireNonBlank(null, "field"))
        assertNotNull(ValidationRules.requireNonBlank("", "field"))
        assertNotNull(ValidationRules.requireNonBlank("   ", "field"))
    }

    @Test
    fun `requireNonBlank enforces max length when specified`() {
        assertNull(ValidationRules.requireNonBlank("short", "field", maxLength = 10))
        val error = ValidationRules.requireNonBlank("this-is-too-long", "field", maxLength = 5)
        assertNotNull(error)
        assertTrue(error!!.contains("cannot exceed 5 characters"))
    }

    @Test
    fun `requireMaxLength allows null, short strings, and rejects oversized strings`() {
        assertNull(ValidationRules.requireMaxLength(null, "field", 10))
        assertNull(ValidationRules.requireMaxLength("short", "field", 10))
        assertNotNull(ValidationRules.requireMaxLength("this is way too long", "field", 5))
    }

    @Test
    fun `requireMinLength allows null, long strings, and rejects undersized strings`() {
        assertNull(ValidationRules.requireMinLength(null, "field", 5))
        assertNull(ValidationRules.requireMinLength("longer-than-five", "field", 5))
        assertNotNull(ValidationRules.requireMinLength("tiny", "field", 5))
    }

    @Test
    fun `requireEmail validates valid and invalid email formats`() {
        assertNull(ValidationRules.requireEmail("user@example.com", "email"))
        assertNull(ValidationRules.requireEmail("test.user+tag@domain.co.uk", "email"))

        assertNotNull(ValidationRules.requireEmail(null, "email"))
        assertNotNull(ValidationRules.requireEmail("", "email"))
        assertNotNull(ValidationRules.requireEmail("invalid-email", "email"))
        assertNotNull(ValidationRules.requireEmail("user@", "email"))
        assertNotNull(ValidationRules.requireEmail("@domain.com", "email"))
        assertNotNull(ValidationRules.requireEmail("user@domain", "email"))
    }

    @Test
    fun `requireUuid validates valid and invalid UUIDs`() {
        val validUuid = UUID.randomUUID().toString()
        assertNull(ValidationRules.requireUuid(validUuid, "id"))

        assertNotNull(ValidationRules.requireUuid(null, "id"))
        assertNotNull(ValidationRules.requireUuid("", "id"))
        assertNotNull(ValidationRules.requireUuid("not-a-uuid", "id"))
        assertNotNull(ValidationRules.requireUuid("12345", "id"))
    }

    @Test
    fun `requirePositiveOrZero accepts 0 and positive numbers, rejects negative numbers`() {
        assertNull(ValidationRules.requirePositiveOrZero(0, "count"))
        assertNull(ValidationRules.requirePositiveOrZero(100, "count"))

        assertNotNull(ValidationRules.requirePositiveOrZero(-1, "count"))
        assertNotNull(ValidationRules.requirePositiveOrZero(-999, "count"))
    }

    @Test
    fun `requireRange enforces boundaries`() {
        assertNull(ValidationRules.requireRange(5, "rating", 1, 10))
        assertNull(ValidationRules.requireRange(1, "rating", 1, 10))
        assertNull(ValidationRules.requireRange(10, "rating", 1, 10))

        assertNotNull(ValidationRules.requireRange(0, "rating", 1, 10))
        assertNotNull(ValidationRules.requireRange(11, "rating", 1, 10))
    }

    @Test
    fun `requireUrl validates HTTP and HTTPS URLs`() {
        assertNull(ValidationRules.requireUrl(null, "url"))
        assertNull(ValidationRules.requireUrl("", "url"))
        assertNull(ValidationRules.requireUrl("https://example.com/icon.png", "url"))
        assertNull(ValidationRules.requireUrl("http://localhost:8080/image", "url"))

        assertNotNull(ValidationRules.requireUrl("ftp://example.com/file", "url"))
        assertNotNull(ValidationRules.requireUrl("not-a-url", "url"))
        assertNotNull(ValidationRules.requireUrl("https://long.com", "url", maxLength = 10))
    }
}
