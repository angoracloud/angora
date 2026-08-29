package cloud.angora.config

import cloud.angora.testsupport.MapSecretsProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SecretsProviderTest {

    @Test
    fun `the default overload returns the fallback only for an absent name`() {
        val secrets = MapSecretsProvider(mapOf("PRESENT" to "value", "BLANK" to ""))

        assertEquals("value", secrets.get("PRESENT", "fallback"))
        assertEquals("fallback", secrets.get("ABSENT", "fallback"))
        assertNull(secrets.get("ABSENT"))
    }

    @Test
    fun `an empty string is a real value, not an absent one`() {
        // CORS_ALLOWED_ORIGINS relies on this: empty means "allow nothing",
        // which must not silently become a default.
        val secrets = MapSecretsProvider(mapOf("BLANK" to ""))

        assertEquals("", secrets.get("BLANK", "fallback"))
    }

    @Test
    fun `Infisical values win over the fallback provider, which still fills gaps`() {
        val provider = InfisicalSecretsProvider(
            secrets = mapOf("SHARED" to "from-infisical"),
            fallback = MapSecretsProvider(
                mapOf("SHARED" to "from-env", "ONLY_ENV" to "from-env")
            )
        )

        assertEquals("from-infisical", provider.get("SHARED"))
        assertEquals("from-env", provider.get("ONLY_ENV"))
        assertNull(provider.get("NEITHER"))
    }

    @Test
    fun `firstOf takes the first name the provider defines`() {
        val secrets = MapSecretsProvider(
            mapOf("DB_PASSWORD" to "from-db-name", "POSTGRES_PASSWORD" to "from-postgres-name")
        )

        assertEquals("from-db-name", secrets.firstOf("DB_PASSWORD", "POSTGRES_PASSWORD"))
    }

    @Test
    fun `firstOf falls through to the compose spelling`() {
        val secrets = MapSecretsProvider(mapOf("POSTGRES_PASSWORD" to "from-postgres-name"))

        assertEquals("from-postgres-name", secrets.firstOf("DB_PASSWORD", "POSTGRES_PASSWORD"))
    }

    @Test
    fun `firstOf returns null when no name is defined, so the config default still applies`() {
        val secrets = MapSecretsProvider(mapOf("UNRELATED" to "value"))

        assertNull(secrets.firstOf("DB_PASSWORD", "POSTGRES_PASSWORD"))
    }

    @Test
    fun `firstOf treats an empty string as defined, not absent`() {
        val secrets = MapSecretsProvider(
            mapOf("DB_USER" to "", "POSTGRES_USER" to "postgres-user")
        )

        assertEquals("", secrets.firstOf("DB_USER", "POSTGRES_USER"))
    }
}
