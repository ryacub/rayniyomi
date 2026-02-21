package eu.kanade.tachiyomi.core.security

/**
 * Authentication methods supported by the app lock system.
 */
sealed interface AuthMethod {
    /**
     * Biometric authentication (fingerprint, face, etc).
     */
    data object Biometric : AuthMethod

    /**
     * PIN-based authentication (4-6 digit PIN).
     */
    data object Pin : AuthMethod

    /**
     * No authentication required.
     */
    data object None : AuthMethod
}
