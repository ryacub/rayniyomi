package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PluginTelemetryTest {

    private lateinit var telemetry: PluginTelemetry

    @BeforeEach
    fun setUp() {
        telemetry = PluginTelemetry()
    }

    // --- Counter logic ---

    @Test
    fun `recordEvent increments success counter for correct stage`() {
        telemetry.recordEvent(PluginStage.FETCH, PluginResult.Success, channel = null)

        val counters = telemetry.getCounters(PluginStage.FETCH)
        assertEquals(1, counters.successCount)
        assertEquals(0, counters.failureCount)
    }

    @Test
    fun `recordEvent increments failure counter for correct stage`() {
        telemetry.recordEvent(
            stage = PluginStage.INSTALL,
            result = PluginResult.Failure(
                reason = PluginFailureReason.CORRUPT_APK,
                isFatal = true,
            ),
            channel = null,
        )

        val counters = telemetry.getCounters(PluginStage.INSTALL)
        assertEquals(0, counters.successCount)
        assertEquals(1, counters.failureCount)
    }

    @Test
    fun `recordEvent does not affect counters for other stages`() {
        telemetry.recordEvent(PluginStage.FETCH, PluginResult.Success, channel = null)

        assertEquals(0, telemetry.getCounters(PluginStage.VERIFY).successCount)
        assertEquals(0, telemetry.getCounters(PluginStage.INSTALL).successCount)
        assertEquals(0, telemetry.getCounters(PluginStage.HANDSHAKE).successCount)
    }

    @Test
    fun `counters accumulate across multiple recordEvent calls`() {
        repeat(3) {
            telemetry.recordEvent(PluginStage.VERIFY, PluginResult.Success, channel = null)
        }
        repeat(2) {
            telemetry.recordEvent(
                stage = PluginStage.VERIFY,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.SIGNATURE_MISMATCH,
                    isFatal = true,
                ),
                channel = null,
            )
        }

        val counters = telemetry.getCounters(PluginStage.VERIFY)
        assertEquals(3, counters.successCount)
        assertEquals(2, counters.failureCount)
    }

    @Test
    fun `StageCounters total is sum of success and failure`() {
        telemetry.recordEvent(PluginStage.FETCH, PluginResult.Success, channel = null)
        telemetry.recordEvent(
            stage = PluginStage.FETCH,
            result = PluginResult.Failure(reason = PluginFailureReason.UNKNOWN, isFatal = false),
            channel = null,
        )
        val counters = telemetry.getCounters(PluginStage.FETCH)
        assertEquals(2, counters.total)
    }

    // --- Threshold alerting ---

    @Test
    fun `getThresholdAlert returns null when total sample count is below 5`() {
        repeat(4) {
            telemetry.recordEvent(
                stage = PluginStage.FETCH,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.NETWORK_TIMEOUT,
                    isFatal = false,
                ),
                channel = null,
            )
        }

        assertNull(telemetry.getThresholdAlert(PluginStage.FETCH))
    }

    @Test
    fun `getThresholdAlert returns alert with correct content when failure rate exceeds 50 percent`() {
        // 4 failures, 1 success = 80% failure rate with 5 samples
        repeat(4) {
            telemetry.recordEvent(
                stage = PluginStage.INSTALL,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.UNKNOWN,
                    isFatal = false,
                ),
                channel = null,
            )
        }
        telemetry.recordEvent(PluginStage.INSTALL, PluginResult.Success, channel = null)

        val alert = telemetry.getThresholdAlert(PluginStage.INSTALL)
        assertNotNull(alert)
        assertEquals(PluginStage.INSTALL, alert!!.stage)
        assertEquals(4, alert.failureCount)
        assertEquals(5, alert.sampleCount)
        assertEquals(0.8, alert.failureRate, 0.001)
    }

    @Test
    fun `getThresholdAlert returns null when failure rate is exactly 50 percent`() {
        // 50% is not strictly greater than 50%
        repeat(5) {
            telemetry.recordEvent(PluginStage.HANDSHAKE, PluginResult.Success, channel = null)
        }
        repeat(5) {
            telemetry.recordEvent(
                stage = PluginStage.HANDSHAKE,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.HANDSHAKE_REJECTED,
                    isFatal = true,
                ),
                channel = null,
            )
        }

        assertNull(telemetry.getThresholdAlert(PluginStage.HANDSHAKE))
    }

    @Test
    fun `getThresholdAlert returns null when failure rate is below 50 percent with enough samples`() {
        repeat(2) {
            telemetry.recordEvent(
                stage = PluginStage.VERIFY,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.VERSION_INCOMPATIBLE,
                    isFatal = false,
                ),
                channel = null,
            )
        }
        repeat(8) {
            telemetry.recordEvent(PluginStage.VERIFY, PluginResult.Success, channel = null)
        }

        assertNull(telemetry.getThresholdAlert(PluginStage.VERIFY))
    }

    @Test
    fun `getThresholdAlert returns null when there are zero samples`() {
        assertNull(telemetry.getThresholdAlert(PluginStage.FETCH))
    }

    @Test
    fun `getThresholdAlert is a pure query with no side effects on subsequent calls`() {
        // Record 4 failures + 1 success to trigger threshold at INSTALL
        repeat(4) {
            telemetry.recordEvent(
                stage = PluginStage.INSTALL,
                result = PluginResult.Failure(reason = PluginFailureReason.UNKNOWN, isFatal = false),
                channel = null,
            )
        }
        telemetry.recordEvent(PluginStage.INSTALL, PluginResult.Success, channel = null)

        // Calling twice must return same counters (no state mutation)
        val first = telemetry.getThresholdAlert(PluginStage.INSTALL)
        val second = telemetry.getThresholdAlert(PluginStage.INSTALL)
        assertEquals(first, second)
    }

    // --- PluginFailureReason taxonomy ---

    @Test
    fun `PluginFailureReason contains exactly the expected taxonomy values`() {
        val expected = setOf(
            "NETWORK_TIMEOUT",
            "SIGNATURE_MISMATCH",
            "VERSION_INCOMPATIBLE",
            "CORRUPT_MANIFEST",
            "CORRUPT_APK",
            "HANDSHAKE_REJECTED",
            "UNKNOWN",
        )
        val actual = PluginFailureReason.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }

    // --- Channel is captured in event ---

    @Test
    fun `recordEvent accepts non-null channel without error`() {
        telemetry.recordEvent(PluginStage.FETCH, PluginResult.Success, channel = "stable")

        val counters = telemetry.getCounters(PluginStage.FETCH)
        assertEquals(1, counters.successCount)
    }
}
