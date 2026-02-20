package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PluginPerformanceBudgetsTest {

    @Test
    fun `STARTUP_CONTRIBUTION budget is 150ms`() {
        assertEquals(150L, PluginPerformanceBudgets.STARTUP_CONTRIBUTION_MS)
    }

    @Test
    fun `MANIFEST_FETCH budget is 5000ms`() {
        assertEquals(5_000L, PluginPerformanceBudgets.MANIFEST_FETCH_MS)
    }

    @Test
    fun `PLUGIN_INSTALL budget is 30000ms`() {
        assertEquals(30_000L, PluginPerformanceBudgets.PLUGIN_INSTALL_MS)
    }

    @Test
    fun `FEATURE_GATE_CHECK budget is 5ms`() {
        assertEquals(5L, PluginPerformanceBudgets.FEATURE_GATE_CHECK_MS)
    }

    @Test
    fun `EPUB_IMPORT budget is 10000ms`() {
        assertEquals(10_000L, PluginPerformanceBudgets.EPUB_IMPORT_MS)
    }

    @Test
    fun `all budget constants are positive`() {
        assert(PluginPerformanceBudgets.STARTUP_CONTRIBUTION_MS > 0)
        assert(PluginPerformanceBudgets.MANIFEST_FETCH_MS > 0)
        assert(PluginPerformanceBudgets.PLUGIN_INSTALL_MS > 0)
        assert(PluginPerformanceBudgets.FEATURE_GATE_CHECK_MS > 0)
        assert(PluginPerformanceBudgets.EPUB_IMPORT_MS > 0)
    }
}
