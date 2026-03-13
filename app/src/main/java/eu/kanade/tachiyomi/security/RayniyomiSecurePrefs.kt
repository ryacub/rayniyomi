package eu.kanade.tachiyomi.security

import android.content.Context
import androidx.annotation.VisibleForTesting

/**
 * Singleton for managing sensitive data in encrypted storage.
 * Stores PIN hash/salt and rayniyomi-specific tracker API tokens using
 * Android Keystore AES-256-GCM encryption via [KeystoreSecureStorage].
 *
 * Usage:
 * - Production: Call `RayniyomiSecurePrefs.init(context)` in App.onCreate before accessing prefs
 * - Testing: Call `RayniyomiSecurePrefs.initForTesting(storage)` in test setup
 */
object RayniyomiSecurePrefs {

    private const val PIN_HASH_KEY = "pin_hash"
    private const val PIN_SALT_KEY = "pin_salt"
    private const val TRACKER_TOKEN_PREFIX = "track_token_"

    private lateinit var storage: SecureStorage

    /**
     * Initialize with Android Keystore-backed storage for production use.
     * Must be called before accessing any preferences.
     */
    fun init(context: Context) {
        storage = KeystoreSecureStorage(context)
    }

    /**
     * Initialize with a test storage implementation for unit testing.
     * Allows tests to skip real Android Keystore operations.
     */
    @VisibleForTesting
    internal fun initForTesting(testStorage: SecureStorage) {
        storage = testStorage
    }

    /** PIN hash for app lock. Returns null if not set. */
    var pinHash: String?
        get() = storage.getString(PIN_HASH_KEY)
        set(value) = storage.putString(PIN_HASH_KEY, value)

    /** PIN salt for app lock. Returns null if not set. */
    var pinSalt: String?
        get() = storage.getString(PIN_SALT_KEY)
        set(value) = storage.putString(PIN_SALT_KEY, value)

    /** Retrieve a tracker API token for the given tracker ID. Returns null if not set. */
    fun getTrackerToken(trackerId: Long): String? =
        storage.getString("$TRACKER_TOKEN_PREFIX$trackerId")

    /** Store or update a tracker API token. Pass null to clear. */
    fun setTrackerToken(trackerId: Long, token: String?) =
        storage.putString("$TRACKER_TOKEN_PREFIX$trackerId", token)
}
