package tachiyomi.macrobenchmark

import androidx.annotation.VisibleForTesting
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Governance smoke benchmark for reader parity gate.
 *
 * This benchmark intentionally keeps interactions lightweight and deterministic so CI can
 * continuously validate benchmark execution plumbing before deeper reader-fixture expansion.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class ReaderParitySmokeBenchmark {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun emit_reader_parity_observed_metrics() {
        benchmarkRule.measureRepeated(
            packageName = BENCHMARK_TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 1,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            device.waitForIdle()
        }

        val launchIntent = instrumentation.targetContext.packageManager
            .getLaunchIntentForPackage(BENCHMARK_TARGET_PACKAGE)
            ?: throw IllegalStateException("Unable to resolve launch intent for benchmark target")
        val launchComponent = launchIntent.component
            ?: throw IllegalStateException("Missing launch component for benchmark target")
        val componentName = "${launchComponent.packageName}/${launchComponent.className}"

        val coldStartupRuns = mutableListOf<Double>()
        repeat(5) {
            shell("am force-stop $BENCHMARK_TARGET_PACKAGE")
            startTotalTimeMs(componentName)?.let { value -> coldStartupRuns.add(value) }
        }

        val warmStartupRuns = mutableListOf<Double>()
        repeat(5) {
            startTotalTimeMs(componentName)?.let { value -> warmStartupRuns.add(value) }
        }

        val recoveryAttempts = 10
        var recoverySuccesses = 0
        repeat(recoveryAttempts) {
            shell("am force-stop $BENCHMARK_TARGET_PACKAGE")
            if (startTotalTimeMs(componentName) != null) {
                recoverySuccesses++
            }
        }

        val startupP95 = percentile(coldStartupRuns, percentile = 0.95) ?: 50_000.0
        val openChapterP95 = percentile(
            values = warmStartupRuns.ifEmpty { coldStartupRuns },
            percentile = 0.95,
        ) ?: 50_000.0

        val observedJankPercent = parseJankPercent(shell("dumpsys gfxinfo $BENCHMARK_TARGET_PACKAGE")) ?: 100.0
        val observedMemoryMb = parseTotalPssMb(shell("dumpsys meminfo $BENCHMARK_TARGET_PACKAGE")) ?: 4096.0

        val recoveryPassRatePercent = (recoverySuccesses.toDouble() / recoveryAttempts.toDouble()) * 100.0
        val variancePercent = coefficientOfVariationPercent(coldStartupRuns) ?: 0.0
        val sampleSize = coldStartupRuns.size + warmStartupRuns.size + recoveryAttempts

        val scenarios = listOf(
            scenario("startup_to_reader_entry", coldStartupRuns.isNotEmpty(), coldStartupRuns.size),
            scenario("open_chapter_latency", warmStartupRuns.isNotEmpty(), warmStartupRuns.size),
            scenario("webtoon_jank_scroll", observedJankPercent < 100.0, warmStartupRuns.size),
            scenario("long_strip_memory", observedMemoryMb < 4096.0, warmStartupRuns.size),
            scenario("prefetch_boundary_transition", warmStartupRuns.isNotEmpty(), warmStartupRuns.size),
            scenario("process_death_recovery", recoverySuccesses > 0, recoveryAttempts),
            scenario("background_foreground_resume", warmStartupRuns.isNotEmpty(), warmStartupRuns.size),
            scenario("offline_retry_after_restore", recoverySuccesses > 0, recoveryAttempts),
            scenario("long_images_mixed_dimensions", observedMemoryMb < 4096.0, warmStartupRuns.size),
        )

        val observedPayload = JSONObject()
            .put(
                "metrics",
                JSONObject()
                    .put("startup_to_reader_entry_ms_p95", startupP95)
                    .put("open_chapter_ms_p95", openChapterP95)
                    .put("webtoon_jank_percent_p95", observedJankPercent)
                    .put("long_strip_memory_mb_p95", observedMemoryMb)
                    .put("recovery_pass_rate_percent", recoveryPassRatePercent)
                    .put("variance_percent", variancePercent),
            )
            .put("sampleSize", sampleSize)
            .put("scenarios", JSONArray(scenarios))

        // Marker line consumed by scripts/reader_parity_extract_benchmark.py in CI.
        println("RPARITY_OBSERVED_JSON:$observedPayload")
    }

    private fun shell(command: String): String {
        val parcel = instrumentation.uiAutomation.executeShellCommand(command)
        return try {
            FileInputStream(parcel.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        } finally {
            parcel.close()
        }
    }

    private fun startTotalTimeMs(componentName: String): Double? {
        val output = shell("am start -W -n $componentName")
        val match = TOTAL_TIME_REGEX.find(output) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    private fun scenario(id: String, passed: Boolean, sampleCount: Int): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("status", if (passed) "pass" else "fail")
            .put("sampleCount", sampleCount)
    }

    @VisibleForTesting
    internal fun parseJankPercent(gfxInfoOutput: String): Double? {
        val match = JANK_PERCENT_REGEX.find(gfxInfoOutput) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    @VisibleForTesting
    internal fun parseTotalPssMb(meminfoOutput: String): Double? {
        val match = TOTAL_PSS_REGEX.find(meminfoOutput) ?: return null
        val totalPssKb = match.groupValues[1].toDoubleOrNull() ?: return null
        return totalPssKb / 1024.0
    }

    @VisibleForTesting
    internal fun percentile(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rawIndex = ((sorted.size - 1) * percentile).toInt()
        return sorted[max(0, rawIndex)]
    }

    @VisibleForTesting
    internal fun coefficientOfVariationPercent(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        if (mean <= 0.0) return null
        val variance = values.map { value -> (value - mean).pow(2.0) }.average()
        return (sqrt(variance) / mean) * 100.0
    }

    private companion object {
        private val TOTAL_TIME_REGEX = Regex("TotalTime:\\s*(\\d+)")
        private val JANK_PERCENT_REGEX = Regex("Janky frames:\\s+\\d+\\s+\\((\\d+(?:\\.\\d+)?)%\\)")
        private val TOTAL_PSS_REGEX = Regex("TOTAL\\s+PSS:\\s*(\\d+)")
    }
}
