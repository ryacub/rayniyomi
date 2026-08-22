package eu.kanade.tachiyomi.security

/**
 * Abstraction over encrypted key-value storage.
 * Production implementation uses Android Keystore + AES-256-GCM.
 * Test implementation uses in-memory maps.
 */
internal interface SecureStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)

    fun putStringSynchronously(key: String, value: String?): Boolean {
        putString(key, value)
        return getString(key) == value
    }
}
