package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PluginPerformanceTrackerTest {

    private lateinit var tracker: PluginPerformanceTracker

    @BeforeEach
    fun setUp() {
        tracker = PluginPerformanceTracker()
    }

    // --- Recording operations ---

    @Test
    fun `recordOperation stores duration for correct category`() {
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 100L)

        val stats = tracker.getStats(OperationCategory.STARTUP)
        assertNotNull(stats)
        assertEquals(1, stats!!.sampleCount)
        assertEquals(100L, stats.p50)
    }

    @Test
    fun `recordOperation accumulates multiple samples`() {
        tracker.recordOperation(OperationCategory.MANIFEST_FETCH, durationMs = 100L)
        tracker.recordOperation(OperationCategory.MANIFEST_FETCH, durationMs = 200L)
        tracker.recordOperation(OperationCategory.MANIFEST_FETCH, durationMs = 300L)

        val stats = tracker.getStats(OperationCategory.MANIFEST_FETCH)
        assertNotNull(stats)
        assertEquals(3, stats!!.sampleCount)
        assertEquals(200L, stats.p50)
    }

    @Test
    fun `recordOperation does not affect other categories`() {
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 100L)

        assertNull(tracker.getStats(OperationCategory.MANIFEST_FETCH))
        assertNull(tracker.getStats(OperationCategory.PLUGIN_INSTALL))
        assertNull(tracker.getStats(OperationCategory.FEATURE_GATE_CHECK))
        assertNull(tracker.getStats(OperationCategory.EPUB_IMPORT))
    }

    // --- Percentile computation ---

    @Test
    fun `getStats computes correct p50 for 3 samples`() {
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 100L)
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 200L)
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 300L)

        val stats = tracker.getStats(OperationCategory.STARTUP)
        assertEquals(200L, stats!!.p50)
    }

    @Test
    fun `getStats computes correct p95 for 100 samples`() {
        // Create 100 samples from 0 to 99
        repeat(100) { i ->
            tracker.recordOperation(OperationCategory.STARTUP, durationMs = i.toLong())
        }

        val stats = tracker.getStats(OperationCategory.STARTUP)
        // p95 should be around index 95 (0-indexed)
        assertTrue(stats!!.p95 >= 90L, "p95=${stats.p95} should be >= 90")
        assertTrue(stats.p95 <= 99L, "p95=${stats.p95} should be <= 99")
    }

    @Test
    fun `getStats computes correct p99 for 100 samples`() {
        // Create 100 samples from 0 to 99
        repeat(100) { i ->
            tracker.recordOperation(OperationCategory.STARTUP, durationMs = i.toLong())
        }

        val stats = tracker.getStats(OperationCategory.STARTUP)
        // p99 should be around index 99 (0-indexed)
        assertTrue(stats!!.p99 >= 95L, "p99=${stats.p99} should be >= 95")
        assertTrue(stats.p99 <= 99L, "p99=${stats.p99} should be <= 99")
    }

    // --- Ring buffer behavior ---

    @Test
    fun `ring buffer retains only last 100 samples`() {
        // Record 150 samples
        repeat(150) { i ->
            tracker.recordOperation(OperationCategory.STARTUP, durationMs = i.toLong())
        }

        val stats = tracker.getStats(OperationCategory.STARTUP)
        // Should only have 100 samples (buffer size)
        assertEquals(100, stats!!.sampleCount)
        // Earliest samples (0-49) should be evicted
        // p50 should be around 50 + 50 = 100 (from samples 50-149)
        assertTrue(stats.p50 >= 90L, "p50=${stats.p50} should reflect newer samples")
    }

    // --- Violation detection ---

    @Test
    fun `checkViolations returns violation when p95 exceeds budget`() {
        // Record samples that put p95 above STARTUP budget (150ms)
        // p95 of 100 samples is index 94 (rank 95), so indexes 94-99 should exceed budget
        repeat(100) { i ->
            val duration = if (i >= 94) 200L else 100L
            tracker.recordOperation(OperationCategory.STARTUP, durationMs = duration)
        }

        val violation = tracker.checkViolations(OperationCategory.STARTUP)
        assertNotNull(violation)
        assertEquals(OperationCategory.STARTUP, violation!!.category)
        assertEquals(PluginPerformanceBudgets.STARTUP_CONTRIBUTION_MS, violation.budgetMs)
        assertTrue(violation.actualP95Ms > PluginPerformanceBudgets.STARTUP_CONTRIBUTION_MS)
    }

    @Test
    fun `checkViolations returns null when p95 is within budget`() {
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 50L)
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 60L)
        tracker.recordOperation(OperationCategory.STARTUP, durationMs = 70L)

        val violation = tracker.checkViolations(OperationCategory.STARTUP)
        assertNull(violation)
    }

    @Test
    fun `checkViolations returns null when category has no samples`() {
        val violation = tracker.checkViolations(OperationCategory.STARTUP)
        assertNull(violation)
    }

    @Test
    fun `checkViolations works for MANIFEST_FETCH category with correct budget`() {
        // Record samples that exceed MANIFEST_FETCH budget (5000ms)
        // p95 of 100 samples is index 94, so indexes 94-99 should exceed budget
        repeat(100) { i ->
            val duration = if (i >= 94) 6_000L else 3_000L
            tracker.recordOperation(OperationCategory.MANIFEST_FETCH, durationMs = duration)
        }

        val violation = tracker.checkViolations(OperationCategory.MANIFEST_FETCH)
        assertNotNull(violation)
        assertEquals(OperationCategory.MANIFEST_FETCH, violation!!.category)
        assertEquals(PluginPerformanceBudgets.MANIFEST_FETCH_MS, violation.budgetMs)
        assertTrue(violation.actualP95Ms > PluginPerformanceBudgets.MANIFEST_FETCH_MS)
    }

    @Test
    fun `checkViolations works for FEATURE_GATE_CHECK category with correct budget`() {
        // Record samples that exceed FEATURE_GATE_CHECK budget (5ms)
        // p95 of 100 samples is index 94, so indexes 94-99 should exceed budget
        repeat(100) { i ->
            val duration = if (i >= 94) 10L else 2L
            tracker.recordOperation(OperationCategory.FEATURE_GATE_CHECK, durationMs = duration)
        }

        val violation = tracker.checkViolations(OperationCategory.FEATURE_GATE_CHECK)
        assertNotNull(violation)
        assertEquals(OperationCategory.FEATURE_GATE_CHECK, violation!!.category)
        assertEquals(PluginPerformanceBudgets.FEATURE_GATE_CHECK_MS, violation.budgetMs)
        assertTrue(violation.actualP95Ms > PluginPerformanceBudgets.FEATURE_GATE_CHECK_MS)
    }

    // --- getStats returns null for empty category ---

    @Test
    fun `getStats returns null when category has no samples`() {
        val stats = tracker.getStats(OperationCategory.STARTUP)
        assertNull(stats)
    }

    // --- OperationCategory enum coverage ---

    @Test
    fun `OperationCategory contains expected categories`() {
        val expected = setOf(
            "STARTUP",
            "MANIFEST_FETCH",
            "PLUGIN_INSTALL",
            "FEATURE_GATE_CHECK",
            "EPUB_IMPORT",
        )
        val actual = OperationCategory.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
