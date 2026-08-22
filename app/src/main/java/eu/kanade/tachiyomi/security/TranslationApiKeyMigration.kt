package eu.kanade.tachiyomi.security

import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/** Migrates the legacy shared translation key and model to one pinned provider. */
object TranslationApiKeyMigration {

    private const val TRANSLATION_PROVIDER = "translation_provider"
    private const val MIGRATION_PROVIDER = "translation_legacy_migration_provider"
    private const val LEGACY_API_KEY = "translation_api_key"
    private const val LEGACY_MODEL = "translation_model"

    internal fun selectedProvider(plainPrefs: SharedPreferences): String? =
        plainPrefs.getString(TRANSLATION_PROVIDER, null)

    fun migrate(plainPrefs: SharedPreferences, providerAtStartup: String? = selectedProvider(plainPrefs)) {
        try {
            synchronized(TranslationCredentialAccess.lock) {
                migrateSafely(plainPrefs, providerAtStartup)
            }
        } catch (error: Exception) {
            logcat(LogPriority.WARN, error) { "Translation BYOK migration failed" }
        }
    }

    private fun migrateSafely(plainPrefs: SharedPreferences, providerAtStartup: String?) {
        val secureLegacyKey = RayniyomiSecurePrefs.translationApiKey
        val plainLegacyKey = plainPrefs.getString(LEGACY_API_KEY, null)
        val legacyKey = secureLegacyKey?.takeIf(String::isNotEmpty)
            ?: plainLegacyKey?.takeIf(String::isNotEmpty)
        val legacyModel = plainPrefs.getString(LEGACY_MODEL, null)?.takeIf(String::isNotEmpty)
        if (legacyKey == null && legacyModel == null) return

        val pinnedProvider = getProvider(plainPrefs.getString(MIGRATION_PROVIDER, null))
        val selectedProvider = getProvider(providerAtStartup)
        val provider = pinnedProvider ?: selectedProvider ?: return

        if (pinnedProvider == null && !plainPrefs.edit().putString(MIGRATION_PROVIDER, provider.name).commit()) {
            return
        }

        val providerId = provider.preferenceId
        val providerKey = RayniyomiSecurePrefs.getTranslationApiKey(providerId)
        if (legacyKey != null && providerKey != null && providerKey != legacyKey) return

        val providerModelKey = "translation_model_$providerId"
        val providerModel = plainPrefs.getString(providerModelKey, null)
        if (legacyModel != null && providerModel != null && providerModel != legacyModel) return

        if (legacyKey != null && providerKey == null) {
            if (!RayniyomiSecurePrefs.setTranslationApiKeySynchronously(providerId, legacyKey)) return
        }
        if (legacyModel != null && providerModel == null) {
            if (!plainPrefs.edit().putString(providerModelKey, legacyModel).commit()) return
        }

        if (secureLegacyKey != null && !RayniyomiSecurePrefs.setLegacyTranslationApiKeySynchronously(null)) {
            return
        }

        val removed = plainPrefs.edit()
            .remove(LEGACY_API_KEY)
            .remove(LEGACY_MODEL)
            .remove(MIGRATION_PROVIDER)
            .commit()
        if (!removed && secureLegacyKey != null) {
            RayniyomiSecurePrefs.setLegacyTranslationApiKeySynchronously(secureLegacyKey)
        }
    }

    private fun getProvider(value: String?): TranslationProvider? {
        return TranslationProvider.entries
            .find { it.name == value }
            ?.takeUnless { it == TranslationProvider.NONE }
    }
}
