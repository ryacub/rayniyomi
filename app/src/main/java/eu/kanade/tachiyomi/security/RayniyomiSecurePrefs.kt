package eu.kanade.tachiyomi.security

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Singleton for managing sensitive data in encrypted SharedPreferences.
 * Stores PIN hash and rayniyomi-specific tracker API tokens securely.
 *
 * Usage:
 * - Production: Call `RayniyomiSecurePrefs.init(context)` in App.onCreate before accessing prefs
 * - Testing: Call `RayniyomiSecurePrefs.initForTesting(fakePrefs)` in test setup
 */
object RayniyomiSecurePrefs {

    private const val PREFS_NAME = "rayniyomi_secure_prefs"
    private const val PIN_HASH_KEY = "pin_hash"
    private const val TRACKER_TOKEN_PREFIX = "track_token_"

    private lateinit var prefs: SharedPreferences

    /**
     * Initialize with real EncryptedSharedPreferences for production use.
     * Must be called before accessing any preferences.
     */
    fun init(context: Context) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Initialize with a test SharedPreferences for unit testing.
     * Allows tests to provide a fake implementation without encryption overhead.
     */
    @VisibleForTesting
    fun initForTesting(testPrefs: SharedPreferences) {
        prefs = testPrefs
    }

    /**
     * PIN hash property for storing and retrieving the user's PIN hash.
     * Returns null if not set.
     */
    var pinHash: String?
        get() = prefs.getString(PIN_HASH_KEY, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(PIN_HASH_KEY)
                } else {
                    putString(PIN_HASH_KEY, value)
                }
                apply()
            }
        }

    /**
     * Retrieve a tracker token for the given tracker ID.
     * Returns null if not set.
     */
    fun getTrackerToken(trackerId: Long): String? {
        val key = "$TRACKER_TOKEN_PREFIX$trackerId"
        return prefs.getString(key, null)
    }

    /**
     * Store or update a tracker token for the given tracker ID.
     * Pass null to clear the token.
     */
    fun setTrackerToken(trackerId: Long, token: String?) {
        val key = "$TRACKER_TOKEN_PREFIX$trackerId"
        prefs.edit().apply {
            if (token == null) {
                remove(key)
            } else {
                putString(key, token)
            }
            apply()
        }
    }
}
