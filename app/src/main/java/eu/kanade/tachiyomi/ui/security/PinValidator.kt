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
 *
 * Centralizes validation logic to avoid duplication across PIN dialogs.
 */
object PinValidator {

    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 6

    /**
     * Validate that a PIN contains only digits (defensive input validation).
     *
     * @param pin The PIN to validate
     * @return Valid if PIN contains only digits, Invalid with error key otherwise
     */
    fun validateFormat(pin: String): PinValidationResult {
        return if (pin.all { it.isDigit() }) {
            PinValidationResult.Valid
        } else {
            PinValidationResult.Invalid("pin_must_be_digits_only")
        }
    }

    /**
     * Validate that a PIN meets length requirements.
     *
     * @param pin The PIN to validate
     * @param minLength Minimum required length (default 4)
     * @param maxLength Maximum required length (default 6)
     * @return Valid if PIN length is within bounds, Invalid with error key otherwise
     */
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

    /**
     * Validate that two PINs match.
     *
     * @param pin1 First PIN
     * @param pin2 Second PIN (confirmation)
     * @return Valid if PINs match, Invalid with error key otherwise
     */
    fun validateMatch(pin1: String, pin2: String): PinValidationResult {
        return if (pin1 == pin2) {
            PinValidationResult.Valid
        } else {
            PinValidationResult.Invalid("pins_dont_match")
        }
    }
}
