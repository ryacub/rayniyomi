package eu.kanade.tachiyomi.ui.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * PIN entry screen with numeric keypad.
 *
 * @param currentPin Current PIN input state
 * @param maxLength Maximum PIN length (6)
 * @param isError Whether to show error state (shake animation)
 * @param errorMessage Error message to display
 * @param isLockedOut Whether user is locked out
 * @param lockoutSecondsRemaining Remaining lockout time
 * @param hasBiometricFallback Whether biometric fallback is available
 * @param onPinChanged Callback when PIN changes
 * @param onSubmit Callback when PIN is submitted (enter pressed)
 * @param onBiometricFallback Callback when "Use Biometric" is pressed
 */
@Composable
fun PinEntryScreen(
    currentPin: String,
    maxLength: Int = 6,
    isError: Boolean = false,
    errorMessage: String? = null,
    isLockedOut: Boolean = false,
    lockoutSecondsRemaining: Int = 0,
    hasBiometricFallback: Boolean = false,
    onPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBiometricFallback: () -> Unit,
) {
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // Trigger shake animation on error
    LaunchedEffect(isError) {
        if (isError) {
            coroutineScope.launch {
                repeat(3) {
                    shakeOffset.animateTo(20f, tween(50))
                    shakeOffset.animateTo(-20f, tween(50))
                }
                shakeOffset.animateTo(0f, tween(50))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.padding.large)
            .graphicsLayer { translationX = shakeOffset.value },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(MR.strings.enter_pin),
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // PIN dots
        PinDots(
            pinLength = currentPin.length,
            maxLength = maxLength,
            isError = isError,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message or lockout countdown
        when {
            isLockedOut -> {
                val lockoutMsg = stringResource(MR.strings.pin_lockout_message, lockoutSecondsRemaining)
                Text(
                    text = lockoutMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = lockoutMsg
                    },
                )
            }
            errorMessage != null -> {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Numeric keypad
        NumericKeypad(
            enabled = !isLockedOut,
            onDigit = { digit ->
                if (currentPin.length < maxLength) {
                    onPinChanged(currentPin + digit)
                }
            },
            onBackspace = {
                if (currentPin.isNotEmpty()) {
                    onPinChanged(currentPin.dropLast(1))
                }
            },
            onSubmit = {
                if (currentPin.length >= 4) {
                    onSubmit()
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Biometric fallback button
        if (hasBiometricFallback) {
            val useBiometricText = stringResource(MR.strings.use_biometric)
            OutlinedButton(
                onClick = onBiometricFallback,
                modifier = Modifier.semantics {
                    contentDescription = useBiometricText
                },
            ) {
                Text(useBiometricText)
            }
        }
    }
}

/**
 * Displays PIN as filled/unfilled dots.
 */
@Composable
private fun PinDots(
    pinLength: Int,
    maxLength: Int,
    isError: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics {
            contentDescription = "PIN length $pinLength of 4 to $maxLength"
        },
    ) {
        repeat(maxLength) { index ->
            Surface(
                modifier = Modifier.size(16.dp),
                shape = CircleShape,
                color = when {
                    isError -> MaterialTheme.colorScheme.error
                    index < pinLength -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
            ) {}
        }
    }
}

/**
 * Numeric keypad (0-9, backspace).
 */
@Composable
private fun NumericKeypad(
    enabled: Boolean,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: 1 2 3
        KeypadRow(
            digits = listOf(1, 2, 3),
            enabled = enabled,
            onDigit = onDigit,
        )

        // Row 2: 4 5 6
        KeypadRow(
            digits = listOf(4, 5, 6),
            enabled = enabled,
            onDigit = onDigit,
        )

        // Row 3: 7 8 9
        KeypadRow(
            digits = listOf(7, 8, 9),
            enabled = enabled,
            onDigit = onDigit,
        )

        // Row 4: empty, 0, backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Empty space
            Spacer(modifier = Modifier.size(72.dp))

            // 0 button
            Button(
                onClick = { onDigit(0) },
                enabled = enabled,
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "Digit 0" },
            ) {
                Text("0", style = MaterialTheme.typography.headlineSmall)
            }

            // Backspace button
            IconButton(
                onClick = onBackspace,
                enabled = enabled,
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "Backspace" },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(
    digits: List<Int>,
    enabled: Boolean,
    onDigit: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        digits.forEach { digit ->
            Button(
                onClick = { onDigit(digit) },
                enabled = enabled,
                modifier = Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "Digit $digit" },
            ) {
                Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Preview
@Composable
private fun PinEntryScreenPreview() {
    PinEntryScreen(
        currentPin = "12",
        isError = false,
        errorMessage = null,
        isLockedOut = false,
        lockoutSecondsRemaining = 0,
        hasBiometricFallback = true,
        onPinChanged = {},
        onSubmit = {},
        onBiometricFallback = {},
    )
}

@Preview
@Composable
private fun PinEntryScreenErrorPreview() {
    PinEntryScreen(
        currentPin = "1234",
        isError = true,
        errorMessage = "Incorrect PIN",
        isLockedOut = false,
        lockoutSecondsRemaining = 0,
        hasBiometricFallback = true,
        onPinChanged = {},
        onSubmit = {},
        onBiometricFallback = {},
    )
}

@Preview
@Composable
private fun PinEntryScreenLockedOutPreview() {
    PinEntryScreen(
        currentPin = "",
        isError = false,
        errorMessage = null,
        isLockedOut = true,
        lockoutSecondsRemaining = 30,
        hasBiometricFallback = false,
        onPinChanged = {},
        onSubmit = {},
        onBiometricFallback = {},
    )
}
