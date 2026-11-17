package eu.kanade.tachiyomi.feature.novel

import logcat.LogPriority
import logcat.LogcatLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies that telemetry logging is skipped when the provided enabled-predicate
 * returns false.
 *
 * This mirrors the contract used by LightNovelPluginManager: it passes
 * `isPluginInstallEnabled()` as the gate so that telemetry events are not
 * logged when the plugin install path is disabled.
 */
class PluginTelemetryGateTest {

    private val messages = mutableListOf<String>()
    private lateinit var telemetry: PluginTelemetry

    @BeforeEach
    fun setUp() {
        messages.clear()
        LogcatLogger.uninstall()
        LogcatLogger.install(
            object : LogcatLogger {
                override fun isLoggable(priority: LogPriority) = true

                override fun log(priority: LogPriority, tag: String, message: String) {
                    messages += message
                }
            },
        )
        telemetry = PluginTelemetry()
    }

    @AfterEach
    fun tearDown() {
        LogcatLogger.uninstall()
    }

    @Test
    fun `recordEvent is a no-op when enabled predicate returns false`() {
        telemetry.recordEvent(
            stage = PluginStage.FETCH,
            result = PluginResult.Success,
            channel = "stable",
            enabled = { false },
        )

        assertEquals(emptyList<String>(), messages)
    }

    @Test
    fun `recordEvent logs when enabled predicate returns true`() {
        telemetry.recordEvent(
            stage = PluginStage.INSTALL,
            result = PluginResult.Failure(
                reason = PluginFailureReason.NETWORK_TIMEOUT,
                isFatal = false,
            ),
            channel = "stable",
            enabled = { true },
        )

        assertEquals(1, messages.size)
    }

    @Test
    fun `recordEvent defaults to enabled when no predicate is provided`() {
        telemetry.recordEvent(PluginStage.VERIFY, PluginResult.Success, channel = null)

        assertEquals(1, messages.size)
    }
}
