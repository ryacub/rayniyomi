package eu.kanade.tachiyomi.ui.security

/**
 * Validation result for PIN operations.
 */
sealed interface PinValidationResult {
    data object Valid : PinValidationResult
    data class Invalid(val errorMessageKey: String) : PinValidationResult
}

/**
 * Utility for validating PIN input.
 */
object PinValidator {

    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 6

    fun validateFormat(pin: String): PinValidationResult {
        return if (pin.all { it.isDigit() }) {
            PinValidationResult.Valid
        } else {
            PinValidationResult.Invalid("pin_must_be_digits_only")
        }
    }

    fun validateLength(
        pin: String,
        minLength: Int = MIN_PIN_LENGTH,
        maxLength: Int = MAX_PIN_LENGTH,
    ): PinValidationResult {
        return when {
            pin.length < minLength -> PinValidationResult.Invalid("pin_must_be_4_digits")
            pin.length > maxLength -> PinValidationResult.Invalid("pin_too_long")
            else -> PinValidationResult.Valid
        }
    }

    fun validateMatch(pin1: String, pin2: String): PinValidationResult {
        return if (pin1 == pin2) {
            PinValidationResult.Valid
        } else {
            PinValidationResult.Invalid("pins_dont_match")
        }
    }
}
