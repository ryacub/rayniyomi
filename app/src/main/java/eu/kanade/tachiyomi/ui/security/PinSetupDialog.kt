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
 * Dialog for setting up a new PIN.
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onPinSet Callback when PIN is successfully set (returns PIN string)
 */
@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit,
) {
    var step by remember { mutableStateOf(PinSetupStep.ENTER) }
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    PinSetupStep.ENTER -> "Create PIN"
                    PinSetupStep.CONFIRM -> "Confirm PIN"
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
                        PinSetupStep.ENTER -> "Enter a 4-6 digit PIN"
                        PinSetupStep.CONFIRM -> "Re-enter your PIN to confirm"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                PinEntryScreen(
                    currentPin = when (step) {
                        PinSetupStep.ENTER -> enteredPin
                        PinSetupStep.CONFIRM -> confirmPin
                    },
                    maxLength = 6,
                    isError = error != null,
                    errorMessage = error,
                    hasBiometricFallback = false,
                    onPinChanged = { newPin ->
                        error = null
                        when (step) {
                            PinSetupStep.ENTER -> enteredPin = newPin
                            PinSetupStep.CONFIRM -> confirmPin = newPin
                        }
                    },
                    onSubmit = {
                        when (step) {
                            PinSetupStep.ENTER -> {
                                if (enteredPin.length < 4) {
                                    error = "PIN must be at least 4 digits"
                                } else {
                                    step = PinSetupStep.CONFIRM
                                    error = null
                                }
                            }
                            PinSetupStep.CONFIRM -> {
                                if (confirmPin == enteredPin) {
                                    onPinSet(enteredPin)
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
                        PinSetupStep.ENTER -> {
                            if (enteredPin.length >= 4) {
                                step = PinSetupStep.CONFIRM
                                error = null
                            } else {
                                error = "PIN must be at least 4 digits"
                            }
                        }
                        PinSetupStep.CONFIRM -> {
                            if (confirmPin == enteredPin) {
                                onPinSet(enteredPin)
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
                        PinSetupStep.ENTER -> "Next"
                        PinSetupStep.CONFIRM -> "Confirm"
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

private enum class PinSetupStep {
    ENTER,
    CONFIRM,
}
