package eu.kanade.tachiyomi.feature.novel

import logcat.LogPriority
import logcat.LogcatLogger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PluginTelemetryTest {

    private data class Line(val priority: LogPriority, val tag: String, val message: String)

    private val lines = mutableListOf<Line>()
    private lateinit var telemetry: PluginTelemetry

    @BeforeEach
    fun setUp() {
        lines.clear()
        LogcatLogger.uninstall()
        LogcatLogger.install(
            object : LogcatLogger {
                override fun isLoggable(priority: LogPriority) = true

                override fun log(priority: LogPriority, tag: String, message: String) {
                    lines += Line(priority, tag, message)
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

    @Test
    fun `success logs INFO line with channel and no duration`() {
        telemetry.recordEvent(PluginStage.FETCH, PluginResult.Success, channel = "stable")

        assertEquals(
            listOf(Line(LogPriority.INFO, "PLUGIN_TELEMETRY", "stage=FETCH result=Success channel=stable")),
            lines,
        )
    }

    @Test
    fun `success logs duration when provided`() {
        telemetry.recordEvent(PluginStage.INSTALL, PluginResult.Success, channel = null, durationMs = 120L)

        assertEquals(
            "stage=INSTALL result=Success channel=null durationMs=120",
            lines.single().message,
        )
    }

    @Test
    fun `fatal failure logs ERROR line with reason`() {
        telemetry.recordEvent(
            stage = PluginStage.VERIFY,
            result = PluginResult.Failure(PluginFailureReason.SIGNATURE_MISMATCH, isFatal = true),
            channel = "stable",
            durationMs = 5L,
        )

        assertEquals(
            Line(
                LogPriority.ERROR,
                "PLUGIN_TELEMETRY",
                "stage=VERIFY result=Failure reason=SIGNATURE_MISMATCH fatal=true channel=stable durationMs=5",
            ),
            lines.single(),
        )
    }

    @Test
    fun `non-fatal failure logs WARN line`() {
        telemetry.recordEvent(
            stage = PluginStage.FETCH,
            result = PluginResult.Failure(PluginFailureReason.NETWORK_TIMEOUT, isFatal = false),
            channel = null,
        )

        assertEquals(
            Line(
                LogPriority.WARN,
                "PLUGIN_TELEMETRY",
                "stage=FETCH result=Failure reason=NETWORK_TIMEOUT fatal=false channel=null",
            ),
            lines.single(),
        )
    }
}
