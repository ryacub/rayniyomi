package eu.kanade.tachiyomi.security

import android.content.SharedPreferences

/**
 * Migrates PIN hash and salt from plaintext SharedPreferences to [RayniyomiSecurePrefs].
 *
 * Idempotent: safe to call multiple times. If the secure store already has the value,
 * it is not overwritten. Old plaintext keys are deleted after migration.
 */
object PinHashMigration {

    private const val PIN_HASH_KEY = "pin_hash"
    private const val PIN_SALT_KEY = "pin_salt"

    fun migrate(plainPrefs: SharedPreferences) {
        val plainHash = plainPrefs.getString(PIN_HASH_KEY, null)

        // Nothing to migrate if plaintext hash is absent or empty
        if (plainHash.isNullOrEmpty()) return

        // Only write to secure store if not already migrated
        if (RayniyomiSecurePrefs.pinHash == null) {
            RayniyomiSecurePrefs.pinHash = plainHash

            val plainSalt = plainPrefs.getString(PIN_SALT_KEY, null)
            if (!plainSalt.isNullOrEmpty()) {
                RayniyomiSecurePrefs.pinSalt = plainSalt
            }
        }

        // Delete old plaintext keys whether or not we just migrated
        plainPrefs.edit()
            .remove(PIN_HASH_KEY)
            .remove(PIN_SALT_KEY)
            .apply()
    }
}
