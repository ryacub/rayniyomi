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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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

    // Load all strings in Composable scope
    val errorMinLength = stringResource(MR.strings.pin_must_be_4_digits)
    val errorMismatch = stringResource(MR.strings.pins_dont_match)
    val actionNext = stringResource(MR.strings.action_next)
    val actionConfirm = stringResource(MR.strings.action_confirm)
    val actionCancel = stringResource(MR.strings.action_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (step) {
                    PinSetupStep.ENTER -> stringResource(MR.strings.create_pin)
                    PinSetupStep.CONFIRM -> stringResource(MR.strings.confirm_pin)
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
                        PinSetupStep.ENTER -> stringResource(MR.strings.pin_setup_enter_prompt)
                        PinSetupStep.CONFIRM -> stringResource(MR.strings.pin_setup_confirm_prompt)
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
                                    error = errorMinLength
                                } else {
                                    step = PinSetupStep.CONFIRM
                                    error = null
                                }
                            }
                            PinSetupStep.CONFIRM -> {
                                if (confirmPin == enteredPin) {
                                    onPinSet(enteredPin)
                                } else {
                                    error = errorMismatch
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
                                error = errorMinLength
                            }
                        }
                        PinSetupStep.CONFIRM -> {
                            if (confirmPin == enteredPin) {
                                onPinSet(enteredPin)
                            } else {
                                error = errorMismatch
                                confirmPin = ""
                            }
                        }
                    }
                },
            ) {
                Text(
                    when (step) {
                        PinSetupStep.ENTER -> actionNext
                        PinSetupStep.CONFIRM -> actionConfirm
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(actionCancel)
            }
        },
    )
}

private enum class PinSetupStep {
    ENTER,
    CONFIRM,
}

@Preview
@Composable
private fun PinSetupDialogPreview() {
    TachiyomiTheme {
        PinSetupDialog(
            onDismiss = {},
            onPinSet = {},
        )
    }
}
