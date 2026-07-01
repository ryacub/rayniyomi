package eu.kanade.tachiyomi.security

import android.content.SharedPreferences

/**
 * Migrates the translation provider API key from plaintext SharedPreferences to
 * [RayniyomiSecurePrefs].
 *
 * Idempotent: safe to call multiple times. If the secure store already has the
 * key, it is not overwritten. The old plaintext key is deleted after migration.
 */
object TranslationApiKeyMigration {

    private const val TRANSLATION_API_KEY = "translation_api_key"

    fun migrate(plainPrefs: SharedPreferences) {
        val plainApiKey = plainPrefs.getString(TRANSLATION_API_KEY, null)

        if (plainApiKey.isNullOrEmpty()) return

        if (RayniyomiSecurePrefs.translationApiKey.isNullOrEmpty()) {
            RayniyomiSecurePrefs.translationApiKey = plainApiKey
        }

        plainPrefs.edit()
            .remove(TRANSLATION_API_KEY)
            .apply()
    }
}
