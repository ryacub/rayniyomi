package eu.kanade.tachiyomi.ui.security

import eu.kanade.tachiyomi.core.security.AuthMethod
import eu.kanade.tachiyomi.core.security.SecurityPreferences

/**
 * Determines which authentication method to show (primary/fallback).
 *
 * Handles logic for resolving the correct auth method based on user preferences
 * and system state, including fallback scenarios when multiple auth methods are enabled.
 */
object AuthenticationOrchestrator {

    /**
     * Resolves the primary authentication method based on user preferences.
     *
     * Priority logic:
     * - If only one auth method enabled, return that method
     * - If both enabled, return the user's primary preference
     * - If neither enabled, return None
     *
     * @param prefs User security preferences
     * @return The primary authentication method to display
     */
    fun resolvePrimaryMethod(prefs: SecurityPreferences): AuthMethod {
        val biometricEnabled = prefs.useAuthenticator().get()
        val pinEnabled = prefs.usePinLock().get()

        return when {
            biometricEnabled && pinEnabled -> {
                // Both enabled - check primary preference
                val primaryPref = prefs.primaryAuthMethod().get()
                if (primaryPref == SecurityPreferences.PrimaryAuthMethod.BIOMETRIC) {
                    AuthMethod.Biometric
                } else {
                    AuthMethod.Pin
                }
            }
            biometricEnabled -> AuthMethod.Biometric
            pinEnabled -> AuthMethod.Pin
            else -> AuthMethod.None
        }
    }

    /**
     * Resolves the fallback authentication method based on the primary method.
     *
     * Fallback logic:
     * - If primary is Biometric, fallback to PIN (if enabled)
     * - If primary is PIN, fallback to Biometric (if enabled)
     * - If the alternate method is not enabled, return None
     *
     * @param primaryMethod The current primary authentication method
     * @param prefs User security preferences
     * @return The fallback authentication method, or None if unavailable
     */
    fun resolveFallbackMethod(primaryMethod: AuthMethod, prefs: SecurityPreferences): AuthMethod {
        return when (primaryMethod) {
            AuthMethod.Biometric -> {
                if (prefs.usePinLock().get()) AuthMethod.Pin else AuthMethod.None
            }
            AuthMethod.Pin -> {
                if (prefs.useAuthenticator().get()) AuthMethod.Biometric else AuthMethod.None
            }
            AuthMethod.None -> AuthMethod.None
        }
    }

    /**
     * Checks if a fallback authentication method is available.
     *
     * @param primaryMethod The current primary authentication method
     * @param prefs User security preferences
     * @return true if a fallback method is available, false otherwise
     */
    fun hasFallbackAvailable(primaryMethod: AuthMethod, prefs: SecurityPreferences): Boolean {
        return resolveFallbackMethod(primaryMethod, prefs) != AuthMethod.None
    }
}
