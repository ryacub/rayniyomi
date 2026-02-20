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

/**
 * Performance statistics for a specific operation category.
 *
 * @property sampleCount Total number of samples recorded.
 * @property p50 50th percentile (median) latency in milliseconds.
 * @property p95 95th percentile latency in milliseconds.
 * @property p99 99th percentile latency in milliseconds.
 */
internal data class PerformanceStats(
    val sampleCount: Int,
    val p50: Long,
    val p95: Long,
    val p99: Long,
)

/**
 * Budget violation alert.
 *
 * @property category The operation category that violated its budget.
 * @property budgetMs The configured budget threshold in milliseconds.
 * @property actualP95Ms The actual p95 latency observed in milliseconds.
 */
internal data class BudgetViolation(
    val category: OperationCategory,
    val budgetMs: Long,
    val actualP95Ms: Long,
)

/**
 * Lightweight, in-process performance tracker for plugin operations.
 *
 * Records operation durations in ring buffers (max 100 samples per category)
 * and computes p50/p95/p99 percentiles. Violations are logged at WARN level
 * when p95 exceeds configured budgets from [PluginPerformanceBudgets].
 *
 * Thread-safe: uses concurrent data structures for lock-free writes.
 *
 * ### Usage
 * ```
 * val tracker = PluginPerformanceTracker()
 * val startNanos = System.nanoTime()
 * // ... perform operation ...
 * val durationMs = (System.nanoTime() - startNanos) / 1_000_000
 * tracker.recordOperation(OperationCategory.STARTUP, durationMs)
 *
 * // Check for violations
 * tracker.checkViolations(OperationCategory.STARTUP)?.let { violation ->
 *     logcat(LogPriority.WARN) { "Budget violation: $violation" }
 * }
 * ```
 *
 * ### Log format
 * ```
 * PLUGIN_PERF_VIOLATION category=STARTUP budgetMs=150 actualP95Ms=200 sampleCount=100
 * ```
 */
internal class PluginPerformanceTracker {

    private companion object {
        /** Maximum samples per category (ring buffer size). */
        private const val MAX_SAMPLES = 100

        private const val LOG_TAG = "PLUGIN_PERF"
    }

    // Ring buffers for each category
    private val buffers = ConcurrentHashMap<OperationCategory, RingBuffer>()

    /**
     * Records an operation duration for the specified category.
     *
     * @param category The operation category.
     * @param durationMs The operation duration in milliseconds.
     */
    fun recordOperation(category: OperationCategory, durationMs: Long) {
        val buffer = buffers.computeIfAbsent(category) { RingBuffer(MAX_SAMPLES) }
        buffer.add(durationMs)
    }

    /**
     * Returns performance statistics for the specified category, or null if
     * no samples have been recorded yet.
     *
     * @param category The operation category.
     * @return Performance statistics, or null if no samples.
     */
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

    /**
     * Checks if the p95 latency for the specified category exceeds its
     * configured budget. Returns a [BudgetViolation] if violated, or null
     * if within budget or no samples recorded.
     *
     * Violations are logged at WARN level.
     *
     * @param category The operation category to check.
     * @return BudgetViolation if violated, null otherwise.
     */
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

    /**
     * Computes the percentile value from a sorted list of samples.
     *
     * Uses nearest-rank method (ceiling): p95 of 100 samples is index 95.
     *
     * @param sorted Sorted list of samples (ascending order).
     * @param percentile Percentile to compute (0-100).
     * @return The percentile value.
     */
    private fun percentile(sorted: List<Long>, percentile: Int): Long {
        if (sorted.isEmpty()) return 0L
        // Nearest-rank method: ceiling of (percentile/100 * n)
        val rank = kotlin.math.ceil(percentile / 100.0 * sorted.size).toInt()
        val index = (rank - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    /**
     * Ring buffer for storing fixed-size sample history.
     */
    private class RingBuffer(private val capacity: Int) {
        private val samples = mutableListOf<Long>()
        private var position = 0

        @Synchronized
        fun add(value: Long) {
            if (samples.size < capacity) {
                samples.add(value)
            } else {
                samples[position] = value
                position = (position + 1) % capacity
            }
        }

        @Synchronized
        fun getSamples(): List<Long> = samples.toList()
    }
}
