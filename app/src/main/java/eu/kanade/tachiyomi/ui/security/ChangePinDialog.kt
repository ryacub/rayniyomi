package eu.kanade.tachiyomi.ui.security

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Dialog for changing an existing PIN.
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onVerifyOldPin Callback to verify old PIN (returns true if correct)
 * @param onPinChanged Callback when PIN is successfully changed (returns new PIN)
 */
@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onVerifyOldPin: (String) -> Boolean,
    onPinChanged: (String) -> Unit,
) {
    var step by remember { mutableStateOf(ChangePinStep.VERIFY_OLD) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    ChangePinStep.VERIFY_OLD -> "Change PIN"
                    ChangePinStep.ENTER_NEW -> "Enter New PIN"
                    ChangePinStep.CONFIRM_NEW -> "Confirm New PIN"
                },
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                },
            )
        },
        text = {
            Column {
                Text(
                    text = when (step) {
                        ChangePinStep.VERIFY_OLD -> "Enter your current PIN"
                        ChangePinStep.ENTER_NEW -> "Enter a new 4-6 digit PIN"
                        ChangePinStep.CONFIRM_NEW -> "Re-enter your new PIN"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                PinEntryScreen(
                    currentPin = when (step) {
                        ChangePinStep.VERIFY_OLD -> oldPin
                        ChangePinStep.ENTER_NEW -> newPin
                        ChangePinStep.CONFIRM_NEW -> confirmPin
                    },
                    maxLength = 6,
                    isError = error != null,
                    errorMessage = error,
                    hasBiometricFallback = false,
                    onPinChanged = { pin ->
                        error = null
                        when (step) {
                            ChangePinStep.VERIFY_OLD -> oldPin = pin
                            ChangePinStep.ENTER_NEW -> newPin = pin
                            ChangePinStep.CONFIRM_NEW -> confirmPin = pin
                        }
                    },
                    onSubmit = {
                        when (step) {
                            ChangePinStep.VERIFY_OLD -> {
                                if (onVerifyOldPin(oldPin)) {
                                    step = ChangePinStep.ENTER_NEW
                                    error = null
                                } else {
                                    error = "Incorrect PIN"
                                    oldPin = ""
                                }
                            }
                            ChangePinStep.ENTER_NEW -> {
                                if (newPin.length < 4) {
                                    error = "PIN must be at least 4 digits"
                                } else {
                                    step = ChangePinStep.CONFIRM_NEW
                                    error = null
                                }
                            }
                            ChangePinStep.CONFIRM_NEW -> {
                                if (confirmPin == newPin) {
                                    onPinChanged(newPin)
                                } else {
                                    error = "PINs don't match"
                                    confirmPin = ""
                                }
                            }
                        }
                    },
                    onBiometricFallback = {},
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (step) {
                        ChangePinStep.VERIFY_OLD -> {
                            if (onVerifyOldPin(oldPin)) {
                                step = ChangePinStep.ENTER_NEW
                                error = null
                            } else {
                                error = "Incorrect PIN"
                                oldPin = ""
                            }
                        }
                        ChangePinStep.ENTER_NEW -> {
                            if (newPin.length >= 4) {
                                step = ChangePinStep.CONFIRM_NEW
                                error = null
                            } else {
                                error = "PIN must be at least 4 digits"
                            }
                        }
                        ChangePinStep.CONFIRM_NEW -> {
                            if (confirmPin == newPin) {
                                onPinChanged(newPin)
                            } else {
                                error = "PINs don't match"
                                confirmPin = ""
                            }
                        }
                    }
                },
            ) {
                Text(
                    when (step) {
                        ChangePinStep.VERIFY_OLD -> "Next"
                        ChangePinStep.ENTER_NEW -> "Next"
                        ChangePinStep.CONFIRM_NEW -> "Confirm"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private enum class ChangePinStep {
    VERIFY_OLD,
    ENTER_NEW,
    CONFIRM_NEW,
}
