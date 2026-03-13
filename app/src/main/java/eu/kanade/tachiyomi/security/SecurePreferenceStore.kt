package eu.kanade.tachiyomi.security

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * A [PreferenceStore] that intercepts specific sensitive keys and routes them to
 * [RayniyomiSecurePrefs] (Android Keystore AES-256-GCM storage) instead of plaintext
 * SharedPreferences. All other keys are delegated to the wrapped store.
 *
 * Used by [eu.kanade.tachiyomi.di.PreferenceModule] to construct [SecurityPreferences]
 * with encrypted backing for PIN hash and salt.
 */
class SecurePreferenceStore(
    private val delegate: PreferenceStore,
) : PreferenceStore by delegate {

    override fun getString(key: String, defaultValue: String): Preference<String> = when (key) {
        "pin_hash" -> SecureStringPreference(
            key = key,
            getter = { RayniyomiSecurePrefs.pinHash },
            setter = { RayniyomiSecurePrefs.pinHash = it },
        )
        "pin_salt" -> SecureStringPreference(
            key = key,
            getter = { RayniyomiSecurePrefs.pinSalt },
            setter = { RayniyomiSecurePrefs.pinSalt = it },
        )
        else -> delegate.getString(key, defaultValue)
    }
}
