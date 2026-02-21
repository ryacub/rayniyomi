package eu.kanade.tachiyomi.core.security

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class SecurityPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun useAuthenticator() = preferenceStore.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = preferenceStore.getInt("lock_app_after", 0)

    fun secureScreen() = preferenceStore.getEnum("secure_screen_v2", SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = preferenceStore.getBoolean("hide_notification_content", false)

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise this pref should be deleted.
     */
    fun lastAppClosed() = preferenceStore.getLong(
        Preference.appStateKey("last_app_closed"),
        0,
    )

    fun usePinLock() = preferenceStore.getBoolean("use_pin_lock", false)

    fun pinHash() = preferenceStore.getString("pin_hash", "")

    fun pinSalt() = preferenceStore.getString("pin_salt", "")

    fun primaryAuthMethod() = preferenceStore.getEnum(
        "primary_auth_method",
        PrimaryAuthMethod.BIOMETRIC,
    )

    fun pinFailedAttempts() = preferenceStore.getInt("pin_failed_attempts", 0)

    fun pinLockoutUntil() = preferenceStore.getLong("pin_lockout_until", 0)

    enum class SecureScreenMode(val titleRes: StringResource) {
        ALWAYS(MR.strings.lock_always),
        INCOGNITO(MR.strings.pref_incognito_mode),
        NEVER(MR.strings.lock_never),
    }

    enum class PrimaryAuthMethod {
        BIOMETRIC,
        PIN,
    }
}
