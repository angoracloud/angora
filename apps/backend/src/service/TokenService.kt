package cloud.angora.service

import cloud.angora.constants.BackendConstants
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface TokenService {
    /** A fresh 256-bit random token, URL-safe base64 encoded. */
    fun generate(): String

    fun hash(token: String): String
}

/**
 * Issues and hashes the opaque tokens behind session cookies and service tokens.
 *
 * SHA-256 rather than Argon2id, deliberately: these are 256 bits of [SecureRandom]
 * output with nothing to guess, so hashing only stops a leaked database yielding
 * usable credentials. It also runs on every authenticated request, where a slow
 * hash would tax the whole API for no security gain.
 */
class TokenServiceImpl(
    private val secureRandom: SecureRandom = SecureRandom()
) : TokenService {

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    override fun generate(): String {
        val bytes = ByteArray(BackendConstants.Auth.TOKEN_BYTES).also { secureRandom.nextBytes(it) }
        return encoder.encodeToString(bytes)
    }

    override fun hash(token: String): String {
        val digest = MessageDigest.getInstance(BackendConstants.Auth.TOKEN_HASH_ALGORITHM)
            .digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
