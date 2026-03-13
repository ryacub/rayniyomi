package eu.kanade.tachiyomi.security

import android.content.SharedPreferences

/**
 * Migrates rayniyomi tracker API tokens from plaintext SharedPreferences to [RayniyomiSecurePrefs].
 *
 * Only migrates keys matching `__PRIVATE_track_token_*`. Other tracker credentials
 * (username, password, auth_expired) remain in plaintext SharedPreferences.
 *
 * Idempotent: safe to call multiple times. Already-migrated values are not overwritten.
 * Old plaintext keys are always deleted after migration.
 */
object TrackerTokenMigration {

    private const val PLAINTEXT_PREFIX = "__PRIVATE_track_token_"

    fun migrate(plainPrefs: SharedPreferences) {
        val editor = plainPrefs.edit()
        var anyFound = false

        for ((key, value) in plainPrefs.all) {
            if (!key.startsWith(PLAINTEXT_PREFIX)) continue

            val trackerId = key.removePrefix(PLAINTEXT_PREFIX).toLongOrNull() ?: continue
            val token = value as? String ?: continue

            // Only write to secure store if not already migrated
            if (RayniyomiSecurePrefs.getTrackerToken(trackerId) == null) {
                RayniyomiSecurePrefs.setTrackerToken(trackerId, token)
            }

            // Delete old plaintext key regardless
            editor.remove(key)
            anyFound = true
        }

        if (anyFound) editor.apply()
    }
}
