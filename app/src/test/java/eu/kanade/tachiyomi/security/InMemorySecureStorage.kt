package eu.kanade.tachiyomi.security

/**
 * In-memory SecureStorage for unit tests.
 * Stores values as plaintext — no encryption overhead needed in unit tests.
 * The real encryption is covered by instrumented tests on device.
 */
internal class InMemorySecureStorage : SecureStorage {
    private val data = mutableMapOf<String, String>()

    override fun getString(key: String): String? = data[key]

    override fun putString(key: String, value: String?) {
        if (value == null) data.remove(key) else data[key] = value
    }
}
