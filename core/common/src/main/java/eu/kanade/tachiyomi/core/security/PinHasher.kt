package eu.kanade.tachiyomi.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Utility for securely hashing and verifying PINs using SHA-256 with salt.
 */
object PinHasher {

    private const val SALT_LENGTH = 32

    /**
     * Hash a PIN using SHA-256 with the provided salt.
     *
     * @param pin The PIN to hash
     * @param salt The cryptographic salt (32 bytes)
     * @return Base64-encoded hash string
     */
    fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashedBytes = digest.digest(pin.toByteArray())
        return Base64.getEncoder().encodeToString(hashedBytes)
    }

    /**
     * Verify a PIN against a stored hash.
     *
     * @param pin The PIN to verify
     * @param storedHash The stored hash (Base64-encoded)
     * @param salt The salt used for hashing (32 bytes)
     * @return true if PIN matches, false otherwise
     */
    fun verify(pin: String, storedHash: String, salt: ByteArray): Boolean {
        val computedHash = hash(pin, salt)
        return computedHash == storedHash
    }

    /**
     * Generate a cryptographically secure random salt.
     *
     * @return 32-byte salt
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
