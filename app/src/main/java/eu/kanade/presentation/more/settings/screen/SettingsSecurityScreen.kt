package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.PinHasher
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.security.ChangePinDialog
import eu.kanade.tachiyomi.ui.security.PinSetupDialog
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import java.util.Base64
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val authSupported = remember { context.isAuthenticationSupported() }

        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        var showPinSetupDialog by remember { mutableStateOf(false) }
        var showChangePinDialog by remember { mutableStateOf(false) }

        if (showPinSetupDialog) {
            PinSetupDialog(
                onDismiss = { showPinSetupDialog = false },
                onPinSet = { pin ->
                    val salt = PinHasher.generateSalt()
                    val hash = PinHasher.hash(pin, salt)
                    securityPreferences.pinHash().set(hash)
                    securityPreferences.pinSalt().set(Base64.getEncoder().encodeToString(salt))
                    showPinSetupDialog = false
                },
            )
        }

        if (showChangePinDialog) {
            ChangePinDialog(
                onDismiss = { showChangePinDialog = false },
                onVerifyOldPin = { oldPin ->
                    val storedHash = securityPreferences.pinHash().get()
                    val storedSalt = Base64.getDecoder().decode(securityPreferences.pinSalt().get())
                    PinHasher.verify(oldPin, storedHash, storedSalt)
                },
                onPinChanged = { newPin ->
                    val salt = PinHasher.generateSalt()
                    val hash = PinHasher.hash(newPin, salt)
                    securityPreferences.pinHash().set(hash)
                    securityPreferences.pinSalt().set(Base64.getEncoder().encodeToString(salt))
                    securityPreferences.pinFailedAttempts().set(0)
                    showChangePinDialog = false
                },
            )
        }

        return buildList {
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    enabled = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_with_biometrics),
                        )
                    },
                ),
            )
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.usePinLock(),
                    title = stringResource(MR.strings.lock_with_pin),
                    onValueChanged = {
                        // Show PIN setup dialog when enabling
                        if (it) {
                            showPinSetupDialog = true
                        }
                        true
                    },
                ),
            )

            val usePinLock by securityPreferences.usePinLock().collectAsState()

            if (usePinLock) {
                add(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.change_pin),
                        onClick = {
                            showChangePinDialog = true
                        },
                    ),
                )
            }

            val useBiometric by useAuthPref.collectAsState()

            if (useBiometric && usePinLock) {
                add(
                    Preference.PreferenceItem.ListPreference(
                        preference = securityPreferences.primaryAuthMethod(),
                        entries = mapOf(
                            SecurityPreferences.PrimaryAuthMethod.BIOMETRIC to stringResource(MR.strings.biometric_default),
                            SecurityPreferences.PrimaryAuthMethod.PIN to stringResource(MR.strings.pin),
                        ).toImmutableMap(),
                        title = stringResource(MR.strings.primary_lock_method),
                    ),
                )
            }

            add(
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter(),
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(
                                    MR.plurals.lock_after_mins,
                                    count = it,
                                    it,
                                )
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.lock_when_idle),
                    enabled = authSupported && useAuth,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_when_idle),
                        )
                    },
                ),
            )
            add(
                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent(),
                    title = stringResource(MR.strings.hide_notification_content),
                ),
            )
            add(
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen(),
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.secure_screen),
                ),
            )
            add(
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
            )
        }
    }
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
