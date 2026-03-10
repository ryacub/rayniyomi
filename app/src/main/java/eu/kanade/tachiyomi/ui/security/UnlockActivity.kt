package eu.kanade.tachiyomi.ui.security

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.core.security.AuthMethod
import eu.kanade.tachiyomi.core.security.LockoutPolicy
import eu.kanade.tachiyomi.core.security.LockoutState
import eu.kanade.tachiyomi.core.security.PinHasher
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.startAuthentication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Base64

/**
 * Blank activity with a BiometricPrompt or PIN entry screen.
 */
class UnlockActivity : BaseActivity() {

    private val securityPreferences: SecurityPreferences by lazy { Injekt.get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val primaryMethod = AuthenticationOrchestrator.resolvePrimaryMethod(securityPreferences)

        when (primaryMethod) {
            AuthMethod.Biometric -> showBiometricAuth()
            AuthMethod.Pin -> showPinAuth()
            AuthMethod.None -> {
                // No auth configured, unlock immediately
                SecureActivityDelegate.unlock()
                finish()
            }
        }
    }

    private fun showBiometricAuth() {
        startAuthentication(
            stringResource(MR.strings.unlock_app_title, stringResource(MR.strings.app_name)),
            confirmationRequired = false,
            callback = object : AuthenticatorUtil.AuthenticationCallback() {
                override fun onAuthenticationError(
                    activity: FragmentActivity?,
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    super.onAuthenticationError(activity, errorCode, errString)
                    logcat(LogPriority.ERROR) { errString.toString() }

                    // Offer PIN fallback if available
                    val fallback = AuthenticationOrchestrator.resolveFallbackMethod(
                        AuthMethod.Biometric,
                        securityPreferences,
                    )
                    if (fallback == AuthMethod.Pin) {
                        showPinAuth()
                    } else {
                        finishAffinity()
                    }
                }

                override fun onAuthenticationSucceeded(
                    activity: FragmentActivity?,
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    super.onAuthenticationSucceeded(activity, result)
                    SecureActivityDelegate.unlock()
                    finish()
                }
            },
        )
    }

    private fun showPinAuth() {
        setContent {
            TachiyomiTheme {
                PinAuthContent()
            }
        }
    }

    @Composable
    private fun PinAuthContent() {
        var currentPin by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var failedAttempts by remember {
            mutableIntStateOf(securityPreferences.pinFailedAttempts().get())
        }
        var lockoutUntil by remember {
            mutableLongStateOf(securityPreferences.pinLockoutUntil().get())
        }
        var lockoutSecondsRemaining by remember { mutableIntStateOf(0) }

        val hasBiometricFallback = AuthenticationOrchestrator.hasFallbackAvailable(
            AuthMethod.Pin,
            securityPreferences,
        )

        // Hoist preference reads to avoid recreating lambda on recomposition
        val storedHash = remember { securityPreferences.pinHash().get() }
        val storedSaltString = remember { securityPreferences.pinSalt().get() }

        val coroutineScope = rememberCoroutineScope()
        val errorFormatAttemptsRemaining = stringResource(MR.strings.incorrect_pin_attempts_remaining)

        LaunchedEffect(lockoutUntil) {
            while (LockoutPolicy.isLockedOut(lockoutUntil)) {
                lockoutSecondsRemaining = LockoutPolicy.calculateRemainingSeconds(lockoutUntil)
                delay(1000)
            }
            lockoutSecondsRemaining = 0
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PinEntryScreen(
                currentPin = currentPin,
                maxLength = 6,
                isError = isError,
                errorMessage = errorMessage,
                isLockedOut = LockoutPolicy.isLockedOut(lockoutUntil),
                lockoutSecondsRemaining = lockoutSecondsRemaining,
                hasBiometricFallback = hasBiometricFallback,
                onPinChanged = { newPin ->
                    currentPin = newPin
                    isError = false
                    errorMessage = null
                },
                onSubmit = {
                    if (LockoutPolicy.isLockedOut(lockoutUntil)) {
                        return@PinEntryScreen
                    }

                    if (storedHash.isEmpty() || storedSaltString.isEmpty()) {
                        logcat(LogPriority.ERROR) { "PIN data corrupted (empty hash or salt), disabling PIN lock" }
                        securityPreferences.usePinLock().set(false)
                        SecureActivityDelegate.unlock()
                        finish()
                        return@PinEntryScreen
                    }

                    val storedSalt = try {
                        Base64.getDecoder().decode(storedSaltString)
                    } catch (e: IllegalArgumentException) {
                        logcat(LogPriority.ERROR, e) { "Failed to decode PIN salt, disabling PIN lock" }
                        securityPreferences.usePinLock().set(false)
                        securityPreferences.pinHash().delete()
                        securityPreferences.pinSalt().delete()
                        SecureActivityDelegate.unlock()
                        finish()
                        return@PinEntryScreen
                    }

                    // Verify PIN on background thread to avoid blocking UI
                    coroutineScope.launch(Dispatchers.IO) {
                        val isValid = PinHasher.verify(currentPin, storedHash, storedSalt)

                        withContext(Dispatchers.Main) {
                            if (isValid) {
                                securityPreferences.pinFailedAttempts().set(0)
                                securityPreferences.pinLockoutUntil().set(0)
                                SecureActivityDelegate.unlock()
                                finish()
                            } else {
                                // Incorrect PIN
                                failedAttempts++
                                securityPreferences.pinFailedAttempts().set(failedAttempts)

                                val lockoutState = LockoutPolicy.calculateLockout(failedAttempts)
                                when (lockoutState) {
                                    LockoutState.Allowed -> {
                                        isError = true
                                        errorMessage = String.format(
                                            errorFormatAttemptsRemaining,
                                            3 - failedAttempts,
                                        )
                                        currentPin = ""
                                    }
                                    is LockoutState.LockedOut -> {
                                        val until = System.currentTimeMillis() + lockoutState.durationMillis
                                        lockoutUntil = until
                                        securityPreferences.pinLockoutUntil().set(until)
                                        isError = true
                                        errorMessage = null
                                        currentPin = ""
                                    }
                                    LockoutState.CloseApp -> {
                                        finishAffinity()
                                    }
                                }
                            }
                        }
                    }
                },
                onBiometricFallback = {
                    showBiometricAuth()
                },
            )
        }
    }
}
