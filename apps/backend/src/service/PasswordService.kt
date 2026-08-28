package cloud.angora.service

import cloud.angora.constants.BackendConstants
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface PasswordService {
    fun hash(plaintext: String): String

    /** Returns false — never throws — for a malformed or unrecognised stored hash. */
    fun verify(plaintext: String, storedHash: String): Boolean

    /**
     * Hashes throwaway input and discards the result, so that a login for an
     * address with no account costs about the same as one with a wrong password.
     * Without it, response time alone reveals which emails have accounts.
     */
    fun dummyVerify()
}

/**
 * Argon2id hashing, storing PHC strings: `$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`.
 *
 * Parameters travel with each hash, so [verify] uses whatever that hash was
 * written with and the cost can be raised later without invalidating existing
 * passwords.
 */
class PasswordServiceImpl(
    private val secureRandom: SecureRandom = SecureRandom()
) : PasswordService {

    private val encoder: Base64.Encoder = Base64.getEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    override fun hash(plaintext: String): String {
        val params = BackendConstants.Auth.Argon2
        val salt = ByteArray(params.SALT_BYTES).also { secureRandom.nextBytes(it) }
        val digest = derive(plaintext, salt, params.MEMORY_KIB, params.ITERATIONS, params.PARALLELISM)

        return buildString {
            append("\$${params.TYPE}")
            append("\$v=${params.VERSION}")
            append("\$m=${params.MEMORY_KIB},t=${params.ITERATIONS},p=${params.PARALLELISM}")
            append("\$${encoder.encodeToString(salt)}")
            append("\$${encoder.encodeToString(digest)}")
        }
    }

    override fun verify(plaintext: String, storedHash: String): Boolean {
        val parsed = parse(storedHash) ?: return false
        val computed = derive(
            plaintext,
            parsed.salt,
            parsed.memoryKib,
            parsed.iterations,
            parsed.parallelism,
            parsed.digest.size
        )
        return MessageDigest.isEqual(computed, parsed.digest)
    }

    override fun dummyVerify() {
        val params = BackendConstants.Auth.Argon2
        derive(
            DUMMY_PLAINTEXT,
            ByteArray(params.SALT_BYTES),
            params.MEMORY_KIB,
            params.ITERATIONS,
            params.PARALLELISM
        )
    }

    private fun derive(
        plaintext: String,
        salt: ByteArray,
        memoryKib: Int,
        iterations: Int,
        parallelism: Int,
        outputBytes: Int = BackendConstants.Auth.Argon2.HASH_BYTES
    ): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKib)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator().apply { init(parameters) }
        return ByteArray(outputBytes).also {
            generator.generateBytes(plaintext.toByteArray(Charsets.UTF_8), it)
        }
    }

    private fun parse(storedHash: String): ParsedHash? {
        val params = BackendConstants.Auth.Argon2

        // The leading '$' yields an empty first element, hence 6 parts.
        val parts = storedHash.split('$')
        if (parts.size != 6) return null
        if (parts[1] != params.TYPE) return null
        if (parts[2] != "v=${params.VERSION}") return null

        val costs = mutableMapOf<String, Int>()
        for (pair in parts[3].split(',')) {
            val key = pair.substringBefore('=', missingDelimiterValue = "")
            val value = pair.substringAfter('=', missingDelimiterValue = "").toIntOrNull()
            if (key.isEmpty() || value == null) return null
            costs[key] = value
        }

        return ParsedHash(
            memoryKib = costs["m"] ?: return null,
            iterations = costs["t"] ?: return null,
            parallelism = costs["p"] ?: return null,
            salt = decodeBase64(parts[4]) ?: return null,
            digest = decodeBase64(parts[5]) ?: return null
        )
    }

    private fun decodeBase64(value: String): ByteArray? =
        try {
            decoder.decode(value)
        } catch (e: IllegalArgumentException) {
            null
        }

    // Not a data class: the generated equals/hashCode would compare ByteArrays by
    // reference, which is never what anyone wants.
    private class ParsedHash(
        val memoryKib: Int,
        val iterations: Int,
        val parallelism: Int,
        val salt: ByteArray,
        val digest: ByteArray
    )

    private companion object {
        const val DUMMY_PLAINTEXT = "angora-dummy-verify"
    }
}
