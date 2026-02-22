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

    val titleChangePin = stringResource(MR.strings.change_pin)
    val titleEnterNew = stringResource(MR.strings.change_pin_enter_new_title)
    val titleConfirmNew = stringResource(MR.strings.change_pin_confirm_new_title)
    val promptOld = stringResource(MR.strings.enter_current_pin)
    val promptNew = stringResource(MR.strings.enter_new_pin)
    val promptConfirmNew = stringResource(MR.strings.change_pin_reenter_new_prompt)
    val errorIncorrect = stringResource(MR.strings.incorrect_pin)
    val errorMinLength = stringResource(MR.strings.pin_must_be_4_digits)
    val errorMismatch = stringResource(MR.strings.pins_dont_match)
    val errorDigitsOnly = stringResource(MR.strings.pin_must_be_digits_only)
    val errorTooLong = stringResource(MR.strings.pin_too_long)
    val actionNext = stringResource(MR.strings.action_next)
    val actionConfirm = stringResource(MR.strings.action_confirm)
    val actionCancel = stringResource(MR.strings.action_cancel)

    val handleSubmit = {
        when (step) {
            ChangePinStep.VERIFY_OLD -> {
                if (onVerifyOldPin(oldPin)) {
                    step = ChangePinStep.ENTER_NEW
                    error = null
                } else {
                    error = errorIncorrect
                    oldPin = ""
                }
            }
            ChangePinStep.ENTER_NEW -> {
                val formatValidation = PinValidator.validateFormat(newPin)
                when (formatValidation) {
                    is PinValidationResult.Valid -> {
                        // Then validate length
                        val lengthValidation = PinValidator.validateLength(newPin)
                        when (lengthValidation) {
                            is PinValidationResult.Valid -> {
                                step = ChangePinStep.CONFIRM_NEW
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
            ChangePinStep.CONFIRM_NEW -> {
                val validation = PinValidator.validateMatch(newPin, confirmPin)
                when (validation) {
                    is PinValidationResult.Valid -> {
                        onPinChanged(newPin)
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
                    ChangePinStep.VERIFY_OLD -> titleChangePin
                    ChangePinStep.ENTER_NEW -> titleEnterNew
                    ChangePinStep.CONFIRM_NEW -> titleConfirmNew
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
                        ChangePinStep.VERIFY_OLD -> promptOld
                        ChangePinStep.ENTER_NEW -> promptNew
                        ChangePinStep.CONFIRM_NEW -> promptConfirmNew
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
                    onSubmit = handleSubmit,
                    onBiometricFallback = {},
                )
            }
        },
        confirmButton = {
            TextButton(onClick = handleSubmit) {
                Text(
                    when (step) {
                        ChangePinStep.VERIFY_OLD -> actionNext
                        ChangePinStep.ENTER_NEW -> actionNext
                        ChangePinStep.CONFIRM_NEW -> actionConfirm
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

private enum class ChangePinStep {
    VERIFY_OLD,
    ENTER_NEW,
    CONFIRM_NEW,
}

@Preview
@Composable
private fun ChangePinDialogPreview() {
    TachiyomiTheme {
        ChangePinDialog(
            onDismiss = {},
            onVerifyOldPin = { false },
            onPinChanged = {},
        )
    }
}
