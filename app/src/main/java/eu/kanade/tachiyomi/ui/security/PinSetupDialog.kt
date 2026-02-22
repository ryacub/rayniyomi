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

    val errorMinLength = stringResource(MR.strings.pin_must_be_4_digits)
    val errorMismatch = stringResource(MR.strings.pins_dont_match)
    val errorDigitsOnly = stringResource(MR.strings.pin_must_be_digits_only)
    val errorTooLong = stringResource(MR.strings.pin_too_long)
    val actionNext = stringResource(MR.strings.action_next)
    val actionConfirm = stringResource(MR.strings.action_confirm)
    val actionCancel = stringResource(MR.strings.action_cancel)

    val handleSubmit = {
        when (step) {
            PinSetupStep.ENTER -> {
                val formatValidation = PinValidator.validateFormat(enteredPin)
                when (formatValidation) {
                    is PinValidationResult.Valid -> {
                        // Then validate length
                        val lengthValidation = PinValidator.validateLength(enteredPin)
                        when (lengthValidation) {
                            is PinValidationResult.Valid -> {
                                step = PinSetupStep.CONFIRM
                                error = null
                            }
                            is PinValidationResult.Invalid -> {
                                error = when (lengthValidation.errorMessageKey) {
                                    "pin_must_be_4_digits" -> errorMinLength
                                    "pin_too_long" -> errorTooLong
                                    else -> errorMinLength
                                }
                            }
                        }
                    }
                    is PinValidationResult.Invalid -> {
                        error = errorDigitsOnly
                    }
                }
            }
            PinSetupStep.CONFIRM -> {
                val validation = PinValidator.validateMatch(enteredPin, confirmPin)
                when (validation) {
                    is PinValidationResult.Valid -> {
                        onPinSet(enteredPin)
                    }
                    is PinValidationResult.Invalid -> {
                        error = errorMismatch
                        confirmPin = ""
                    }
                }
            }
        }
    }

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
                    onSubmit = handleSubmit,
                    onBiometricFallback = {},
                )
            }
        },
        confirmButton = {
            TextButton(onClick = handleSubmit) {
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
