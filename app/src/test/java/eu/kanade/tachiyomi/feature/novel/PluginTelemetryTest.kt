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
                reason = PluginFailureReason.CORRUPT_MANIFEST.name,
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
                    reason = PluginFailureReason.SIGNATURE_MISMATCH.name,
                    isFatal = true,
                ),
                channel = null,
            )
        }

        val counters = telemetry.getCounters(PluginStage.VERIFY)
        assertEquals(3, counters.successCount)
        assertEquals(2, counters.failureCount)
    }

    // --- Threshold alerting ---

    @Test
    fun `getThresholdAlert returns null when total sample count is below 5`() {
        repeat(4) {
            telemetry.recordEvent(
                stage = PluginStage.FETCH,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.NETWORK_TIMEOUT.name,
                    isFatal = false,
                ),
                channel = null,
            )
        }

        assertNull(telemetry.getThresholdAlert(PluginStage.FETCH))
    }

    @Test
    fun `getThresholdAlert returns alert when failure rate exceeds 50 percent with 5 or more samples`() {
        // 4 failures, 1 success = 80% failure rate with 5 samples
        repeat(4) {
            telemetry.recordEvent(
                stage = PluginStage.INSTALL,
                result = PluginResult.Failure(
                    reason = PluginFailureReason.UNKNOWN.name,
                    isFatal = false,
                ),
                channel = null,
            )
        }
        telemetry.recordEvent(PluginStage.INSTALL, PluginResult.Success, channel = null)

        val alert = telemetry.getThresholdAlert(PluginStage.INSTALL)
        assertNotNull(alert)
        assertEquals(PluginStage.INSTALL, alert!!.stage)
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
                    reason = PluginFailureReason.HANDSHAKE_REJECTED.name,
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
                    reason = PluginFailureReason.VERSION_INCOMPATIBLE.name,
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

    // --- PluginFailureReason taxonomy ---

    @Test
    fun `PluginFailureReason includes all required taxonomy values`() {
        val reasons = PluginFailureReason.entries.map { it.name }
        assert(PluginFailureReason.NETWORK_TIMEOUT.name in reasons)
        assert(PluginFailureReason.SIGNATURE_MISMATCH.name in reasons)
        assert(PluginFailureReason.VERSION_INCOMPATIBLE.name in reasons)
        assert(PluginFailureReason.CORRUPT_MANIFEST.name in reasons)
        assert(PluginFailureReason.HANDSHAKE_REJECTED.name in reasons)
        assert(PluginFailureReason.UNKNOWN.name in reasons)
    }

    // --- Channel is captured in event ---

    @Test
    fun `recordEvent accepts non-null channel without error`() {
        telemetry.recordEvent(PluginStage.FETCH, PluginResult.Success, channel = "stable")

        val counters = telemetry.getCounters(PluginStage.FETCH)
        assertEquals(1, counters.successCount)
    }
}
