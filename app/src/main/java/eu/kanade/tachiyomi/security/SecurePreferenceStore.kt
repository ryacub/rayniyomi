package eu.kanade.tachiyomi.security

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * A [PreferenceStore] that intercepts specific sensitive keys and routes them to
 * [RayniyomiSecurePrefs] (Android Keystore AES-256-GCM storage) instead of plaintext
 * SharedPreferences. All other keys are delegated to the wrapped store.
 *
 * Secure keys handled:
 * - `pin_hash` / `pin_salt` — PIN lock credentials
 * - `__PRIVATE_track_token_<id>` — rayniyomi tracker API tokens
 * - `translation_api_key_<provider>` — translation provider API keys
 *
 * Used by [eu.kanade.tachiyomi.di.PreferenceModule] to construct [SecurityPreferences],
 * [eu.kanade.domain.track.service.TrackPreferences], and
 * [eu.kanade.tachiyomi.data.translation.TranslationPreferences] with encrypted backing.
 */
class SecurePreferenceStore(
    private val delegate: PreferenceStore,
) : PreferenceStore by delegate {

    override fun getString(key: String, defaultValue: String): Preference<String> = when {
        key == "pin_hash" -> SecureStringPreference(
            key = key,
            getter = { RayniyomiSecurePrefs.pinHash },
            setter = { RayniyomiSecurePrefs.pinHash = it },
        )
        key == "pin_salt" -> SecureStringPreference(
            key = key,
            getter = { RayniyomiSecurePrefs.pinSalt },
            setter = { RayniyomiSecurePrefs.pinSalt = it },
        )
        key.startsWith(TRANSLATION_API_KEY_PREFIX) -> LockedPreference(
            SecureStringPreference(
                key = key,
                getter = { RayniyomiSecurePrefs.getTranslationApiKey(key.removePrefix(TRANSLATION_API_KEY_PREFIX)) },
                setter = {
                    RayniyomiSecurePrefs.setTranslationApiKey(
                        key.removePrefix(TRANSLATION_API_KEY_PREFIX),
                        it,
                    )
                },
            ),
        )
        key.startsWith(TRANSLATION_MODEL_PREFIX) -> LockedPreference(delegate.getString(key, defaultValue))
        key.startsWith(TRACKER_TOKEN_PREFIX) -> {
            val trackerId = key.removePrefix(TRACKER_TOKEN_PREFIX).toLongOrNull()
            if (trackerId != null) {
                SecureStringPreference(
                    key = key,
                    getter = { RayniyomiSecurePrefs.getTrackerToken(trackerId) },
                    setter = { RayniyomiSecurePrefs.setTrackerToken(trackerId, it) },
                )
            } else {
                delegate.getString(key, defaultValue)
            }
        }
        else -> delegate.getString(key, defaultValue)
    }

    private companion object {
        const val TRACKER_TOKEN_PREFIX = "__PRIVATE_track_token_"
        const val TRANSLATION_API_KEY_PREFIX = "translation_api_key_"
        const val TRANSLATION_MODEL_PREFIX = "translation_model_"
    }
}
