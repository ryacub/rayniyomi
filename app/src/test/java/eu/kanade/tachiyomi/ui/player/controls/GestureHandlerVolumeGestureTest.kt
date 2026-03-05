package eu.kanade.tachiyomi.ui.player.controls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

/**
 * Unit tests for volume gesture normalization (R419).
 *
 * These tests verify that volume gestures are tracked in normalized [0.0, 1.0] float space,
 * ensuring the same physical swipe produces consistent volume changes across all devices
 * regardless of their audio step count (15-25 steps).
 *
 * The approach uses the existing Float overload of calculateNewVerticalGestureValue
 * to compute normalized deltas, then converts to integer steps only when calling changeVolumeTo().
 */
class GestureHandlerVolumeGestureTest {

    /**
     * Test that upward swipe (startingY > newY) increases normalized volume.
     * 100px upward swipe with 0.001f sensitivity → 0.1f delta increase.
     *
     * This tests the core behavior: upward gesture produces positive delta,
     * moving normalized volume toward 1.0f regardless of device maxVolume.
     */
    @Test
    fun `upward swipe increases normalized volume`() {
        val originalVolumeNorm = 0.5f
        val startingY = 200f
        val newY = 100f // 100px upward
        val sensitivity = 0.001f

        val result = calculateNewVerticalGestureValue(
            originalVolumeNorm,
            startingY,
            newY,
            sensitivity,
        )

        // 0.5 + ((200 - 100) * 0.001) = 0.5 + 0.1 = 0.6
        assertEquals(0.6f, result, 0.0001f)
    }

    /**
     * Test that downward swipe (startingY < newY) decreases normalized volume.
     * 50px downward swipe with 0.001f sensitivity → 0.05f delta decrease.
     *
     * Symmetric to upward: downward gesture produces negative delta,
     * moving normalized volume toward 0.0f.
     */
    @Test
    fun `downward swipe decreases normalized volume`() {
        val originalVolumeNorm = 0.5f
        val startingY = 100f
        val newY = 150f // 50px downward
        val sensitivity = 0.001f

        val result = calculateNewVerticalGestureValue(
            originalVolumeNorm,
            startingY,
            newY,
            sensitivity,
        )

        // 0.5 + ((100 - 150) * 0.001) = 0.5 - 0.05 = 0.45
        assertEquals(0.45f, result, 0.0001f)
    }

    /**
     * Test that large upward swipe is clamped to [0.0, 1.0] bounds using coerceIn.
     * A swipe that would exceed 1.0f must be clamped to 1.0f.
     *
     * This ensures normalized volume never exceeds maximum, preventing invalid states
     * that could overflow when converting to integer steps.
     */
    @Test
    fun `upward swipe clamped to upper bound of 1_0f`() {
        val originalVolumeNorm = 0.95f
        val startingY = 200f
        val newY = 0f // 200px upward → large delta
        val sensitivity = 0.001f

        val result = calculateNewVerticalGestureValue(
            originalVolumeNorm,
            startingY,
            newY,
            sensitivity,
        ).coerceIn(0f, 1f)

        // 0.95 + ((200 - 0) * 0.001) = 0.95 + 0.2 = 1.15 → clamped to 1.0
        assertEquals(1.0f, result, 0.0001f)
    }

    /**
     * Test that large downward swipe is clamped to [0.0, 1.0] bounds using coerceIn.
     * A swipe that would go below 0.0f must be clamped to 0.0f.
     *
     * Ensures normalized volume never becomes negative, preventing invalid states.
     */
    @Test
    fun `downward swipe clamped to lower bound of 0_0f`() {
        val originalVolumeNorm = 0.05f
        val startingY = 100f
        val newY = 400f // 300px downward → large negative delta
        val sensitivity = 0.001f

        val result = calculateNewVerticalGestureValue(
            originalVolumeNorm,
            startingY,
            newY,
            sensitivity,
        ).coerceIn(0f, 1f)

        // 0.05 + ((100 - 400) * 0.001) = 0.05 - 0.3 = -0.25 → clamped to 0.0
        assertEquals(0.0f, result, 0.0001f)
    }

    /**
     * Test conversion from normalized float to integer steps using roundToInt.
     * 0.6f normalized volume × 15 steps = 9 (integer volume level).
     *
     * This verifies the conversion chain at the ViewModel boundary:
     * (normalizedVolume.coerceIn(0f, 1f) * maxVolume).roundToInt()
     */
    @Test
    fun `normalized volume converts to integer steps with roundToInt`() {
        val normalizedVolume = 0.6f
        val maxVolume = 15
        val expectedIntVolume = 9

        val intVolume = (normalizedVolume * maxVolume).roundToInt()

        assertEquals(expectedIntVolume, intVolume)
    }

    /**
     * Test roundToInt rounding behavior for edge cases.
     * 0.5333f * 15 = 8.0f → should round to 8, not 9.
     *
     * This ensures the rounding strategy is correctly applied when converting
     * float deltas to integer volume steps.
     */
    @Test
    fun `roundToInt handles fractional steps correctly`() {
        val normalizedVolume = 0.5333f
        val maxVolume = 15

        val intVolume = (normalizedVolume * maxVolume).roundToInt()

        // 0.5333 * 15 = 8.0 → rounds to 8
        assertEquals(8, intVolume)
    }

    /**
     * Test that small gestures produce consistent fractional deltas in normalized space.
     * 1px upward swipe with 0.001f sensitivity → 0.001f delta (device-independent).
     *
     * Key behavioral requirement: The same physical gesture (1px) produces the same
     * fractional volume change (0.001f) on all devices, proving gesture independence.
     */
    @Test
    fun `small gesture produces consistent normalized delta across devices`() {
        val originalVolumeNorm = 0.5f
        val startingY = 100f
        val newY = 99f // 1px upward
        val sensitivity = 0.001f

        val result = calculateNewVerticalGestureValue(
            originalVolumeNorm,
            startingY,
            newY,
            sensitivity,
        )

        // 0.5 + ((100 - 99) * 0.001) = 0.5 + 0.001 = 0.501
        assertEquals(0.501f, result, 0.0001f)

        // On a 15-step device: 0.501 * 15 = 7.515 → 8 steps
        val device15Step = (result * 15).roundToInt()
        // On a 25-step device: 0.501 * 25 = 12.525 → 13 steps
        val device25Step = (result * 25).roundToInt()

        // Both devices see the same normalized delta (0.001f),
        // so the gesture behavior is consistent even though the integer steps differ.
        assertEquals(8, device15Step)
        assertEquals(13, device25Step)
    }

    /**
     * Test full conversion chain: gesture → normalized float → clamped → integer steps.
     * Simulate a user swiping from 50% volume up by 150px on a 15-step device.
     *
     * This end-to-end test verifies the complete normalization flow:
     * 1. Capture original normalized volume (0.5 on a 15-step device)
     * 2. Calculate new normalized value via gesture delta
     * 3. Coerce to [0, 1] bounds
     * 4. Convert to integer steps for changeVolumeTo()
     */
    @Test
    fun `full conversion chain from gesture to integer volume`() {
        val maxVolume = 15
        val originalIntVolume = 7 // ~50% on 15-step device
        val originalVolumeNorm = originalIntVolume.toFloat() / maxVolume // 0.4666...
        val startingY = 200f
        val newY = 50f // 150px upward
        val sensitivity = 0.001f

        val newNormalizedVolume = calculateNewVerticalGestureValue(
            originalVolumeNorm,
            startingY,
            newY,
            sensitivity,
        ).coerceIn(0f, 1f)

        val newIntVolume = (newNormalizedVolume * maxVolume).roundToInt()

        // 0.4666 + 0.15 = 0.6166, which converts to 9 steps on 15-step device
        assertEquals(9, newIntVolume)

        // Verify that the same gesture on a 25-step device produces proportional change
        val maxVolume25 = 25
        val originalVolumeNorm25 = originalIntVolume.toFloat() / maxVolume25 // 0.28
        val newNormalizedVolume25 = calculateNewVerticalGestureValue(
            originalVolumeNorm25,
            startingY,
            newY,
            sensitivity,
        ).coerceIn(0f, 1f)
        val newIntVolume25 = (newNormalizedVolume25 * maxVolume25).roundToInt()

        // Same gesture on 25-step device: 0.28 + 0.15 = 0.43 * 25 = 10.75 → 11 steps
        // This shows consistent gesture behavior across devices
        assertEquals(11, newIntVolume25)
    }
}
