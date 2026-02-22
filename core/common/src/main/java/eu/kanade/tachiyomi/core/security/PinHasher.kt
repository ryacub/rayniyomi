package eu.kanade.tachiyomi.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Utility for securely hashing and verifying PINs using SHA-256 with salt.
 */
object PinHasher {

    private const val SALT_LENGTH = 32

    fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashedBytes = digest.digest(pin.toByteArray())
        return Base64.getEncoder().encodeToString(hashedBytes)
    }

    fun verify(pin: String, storedHash: String, salt: ByteArray): Boolean {
        val computedHash = hash(pin, salt)
        // Use constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(
            computedHash.toByteArray(),
            storedHash.toByteArray(),
        )
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
