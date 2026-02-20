package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies that telemetry recording is skipped (no counter increments)
 * when the provided enabled-predicate returns false.
 *
 * This mirrors the contract used by LightNovelPluginManager: it passes
 * `isPluginInstallEnabled()` as the gate so that telemetry events are not
 * recorded when the plugin install path is disabled.
 */
class PluginTelemetryGateTest {

    private lateinit var telemetry: PluginTelemetry

    @BeforeEach
    fun setUp() {
        telemetry = PluginTelemetry()
    }

    @Test
    fun `recordEvent is a no-op when enabled predicate returns false`() {
        telemetry.recordEvent(
            stage = PluginStage.FETCH,
            result = PluginResult.Success,
            channel = "stable",
            enabled = { false },
        )

        val counters = telemetry.getCounters(PluginStage.FETCH)
        assertEquals(0, counters.successCount)
        assertEquals(0, counters.failureCount)
    }

    @Test
    fun `recordEvent increments counters when enabled predicate returns true`() {
        telemetry.recordEvent(
            stage = PluginStage.INSTALL,
            result = PluginResult.Failure(
                reason = PluginFailureReason.NETWORK_TIMEOUT,
                isFatal = false,
            ),
            channel = "beta",
            enabled = { true },
        )

        val counters = telemetry.getCounters(PluginStage.INSTALL)
        assertEquals(0, counters.successCount)
        assertEquals(1, counters.failureCount)
    }

    @Test
    fun `recordEvent defaults to enabled when no predicate is provided`() {
        // Default overload (no enabled param) should always record.
        telemetry.recordEvent(PluginStage.VERIFY, PluginResult.Success, channel = null)

        val counters = telemetry.getCounters(PluginStage.VERIFY)
        assertEquals(1, counters.successCount)
    }
}
