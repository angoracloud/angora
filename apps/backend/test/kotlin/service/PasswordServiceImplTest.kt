package cloud.angora.service

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class PasswordServiceImplTest {

    private val service = PasswordServiceImpl()

    @Test
    fun `verifies a password against its own hash`() {
        val hash = service.hash("correct horse battery staple")

        assertTrue(service.verify("correct horse battery staple", hash))
    }

    @Test
    fun `rejects a wrong password`() {
        val hash = service.hash("correct horse battery staple")

        assertFalse(service.verify("Correct horse battery staple", hash))
        assertFalse(service.verify("", hash))
    }

    @Test
    fun `hashes the same password differently each time`() {
        val first = service.hash("same-password")
        val second = service.hash("same-password")

        // Distinct random salts, so identical passwords must not collide in the
        // database — otherwise the hashes themselves reveal who shares a password.
        assertNotEquals(first, second)
        assertTrue(service.verify("same-password", first))
        assertTrue(service.verify("same-password", second))
    }

    @Test
    fun `writes a PHC string carrying the parameters used`() {
        val hash = service.hash("whatever")
        val parts = hash.split('$')

        assertEquals(6, parts.size)
        assertEquals("", parts[0])
        assertEquals("argon2id", parts[1])
        assertEquals("v=19", parts[2])
        assertEquals("m=19456,t=2,p=1", parts[3])
    }

    @Test
    fun `verifies a hash written with different cost parameters`() {
        // Parameters travel with the hash, so raising the cost later must not
        // invalidate passwords hashed under the old settings.
        val cheaperHash = "\$argon2id\$v=19\$m=8192,t=1,p=1\$" +
            "c2FsdHNhbHRzYWx0c2FsdA\$" +
            hashWith("hunter2", memoryKib = 8192, iterations = 1)

        assertTrue(service.verify("hunter2", cheaperHash))
        assertFalse(service.verify("hunter3", cheaperHash))
    }

    @Test
    fun `returns false rather than throwing for a malformed stored hash`() {
        listOf(
            "",
            "not-a-hash",
            "\$argon2id\$v=19\$m=19456,t=2,p=1\$onlyfourparts",
            "\$bcrypt\$v=19\$m=19456,t=2,p=1\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=16\$m=19456,t=2,p=1\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=19\$m=notanumber,t=2,p=1\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=19\$t=2,p=1\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=19\$m=19456,t=2,p=1\$!!!not-base64!!!\$aGFzaA",
            // Cost segments with no '=' at all. These are the shapes that used to
            // throw IndexOutOfBoundsException out of the parser instead of
            // returning false, turning a corrupted row into a 500.
            "\$argon2id\$v=19\$m,t=2,p=1\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=19\$m=1,t=2,p\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=19\$\$c2FsdA\$aGFzaA",
            "\$argon2id\$v=19\$m=1,=2,p=1\$c2FsdA\$aGFzaA"
        ).forEach { malformed ->
            assertFalse(service.verify("hunter2", malformed), "expected false for: $malformed")
        }
    }

    /**
     * Derives a digest the same way [PasswordServiceImpl] does, so the test can
     * build a hash with non-default cost parameters and a known salt.
     */
    private fun hashWith(password: String, memoryKib: Int, iterations: Int): String {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(1)
            .withSalt("saltsaltsaltsalt".toByteArray())
            .build()

        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password.toByteArray(), out)

        return Base64.getEncoder().withoutPadding().encodeToString(out)
    }
}
