package eu.kanade.tachiyomi.feature.novel

import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap

/**
 * Categories of plugin operations tracked for performance monitoring.
 */
internal enum class OperationCategory {
    /** Cold-start initialization overhead. */
    STARTUP,

    /** Remote manifest retrieval latency. */
    MANIFEST_FETCH,

    /** APK download and install handoff latency. */
    PLUGIN_INSTALL,

    /** Feature flag evaluation latency. */
    FEATURE_GATE_CHECK,

    /** EPUB file import/parsing latency. */
    EPUB_IMPORT,
}

internal data class PerformanceStats(
    val sampleCount: Int,
    val p50: Long,
    val p95: Long,
    val p99: Long,
)

internal data class BudgetViolation(
    val category: OperationCategory,
    val budgetMs: Long,
    val actualP95Ms: Long,
)

/**
 * Tracks plugin operation durations and detects budget violations.
 * Records samples in ring buffers and logs WARN when p95 exceeds thresholds.
 */
internal class PluginPerformanceTracker {

    private companion object {
        /** Maximum samples per category (ring buffer size). */
        private const val MAX_SAMPLES = 100

        private const val LOG_TAG = "PLUGIN_PERF"
    }

    // Ring buffers for each category
    private val buffers = ConcurrentHashMap<OperationCategory, PerformanceRingBuffer>()

    fun recordOperation(category: OperationCategory, durationMs: Long) {
        val buffer = buffers.computeIfAbsent(category) { PerformanceRingBuffer(MAX_SAMPLES) }
        buffer.add(durationMs)
    }

    fun getStats(category: OperationCategory): PerformanceStats? {
        val buffer = buffers[category] ?: return null
        val samples = buffer.getSamples()
        if (samples.isEmpty()) return null

        val sorted = samples.sorted()
        return PerformanceStats(
            sampleCount = samples.size,
            p50 = percentile(sorted, 50),
            p95 = percentile(sorted, 95),
            p99 = percentile(sorted, 99),
        )
    }

    fun checkViolations(category: OperationCategory): BudgetViolation? {
        val stats = getStats(category) ?: return null
        val budget = getBudget(category)

        if (stats.p95 > budget) {
            val violation = BudgetViolation(
                category = category,
                budgetMs = budget,
                actualP95Ms = stats.p95,
            )
            logcat(LOG_TAG, LogPriority.WARN) {
                "category=${violation.category} budgetMs=${violation.budgetMs} " +
                    "actualP95Ms=${violation.actualP95Ms} sampleCount=${stats.sampleCount}"
            }
            return violation
        }

        return null
    }

    private fun getBudget(category: OperationCategory): Long {
        return when (category) {
            OperationCategory.STARTUP -> PluginPerformanceBudgets.STARTUP_CONTRIBUTION_MS
            OperationCategory.MANIFEST_FETCH -> PluginPerformanceBudgets.MANIFEST_FETCH_MS
            OperationCategory.PLUGIN_INSTALL -> PluginPerformanceBudgets.PLUGIN_INSTALL_MS
            OperationCategory.FEATURE_GATE_CHECK -> PluginPerformanceBudgets.FEATURE_GATE_CHECK_MS
            OperationCategory.EPUB_IMPORT -> PluginPerformanceBudgets.EPUB_IMPORT_MS
        }
    }
}
