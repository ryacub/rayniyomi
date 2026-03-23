package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Tests for plugin update policy: min-version enforcement and rollback.
 *
 * TDD: these tests were written before the implementation to drive the design.
 */
class LightNovelUpdatePolicyTest {

    // ── Min-version enforcement (evaluateVersionPolicy) ─────────────────────

    @Test
    fun `evaluateVersionPolicy returns compatible when plugin version meets minimum`() {
        val result = evaluateVersionPolicy(
            pluginVersionCode = 200L,
            minPluginVersionCode = 100L,
        )

        assertEquals(VersionPolicyResult.COMPATIBLE, result)
    }

    @Test
    fun `evaluateVersionPolicy returns compatible when plugin version equals minimum`() {
        val result = evaluateVersionPolicy(
            pluginVersionCode = 100L,
            minPluginVersionCode = 100L,
        )

        assertEquals(VersionPolicyResult.COMPATIBLE, result)
    }

    @Test
    fun `evaluateVersionPolicy returns plugin too old when plugin version is below minimum`() {
        val result = evaluateVersionPolicy(
            pluginVersionCode = 99L,
            minPluginVersionCode = 100L,
        )

        assertEquals(VersionPolicyResult.PLUGIN_TOO_OLD, result)
    }

    @Test
    fun `evaluateVersionPolicy returns compatible when minPluginVersionCode is zero`() {
        val result = evaluateVersionPolicy(
            pluginVersionCode = 1L,
            minPluginVersionCode = 0L,
        )

        assertEquals(VersionPolicyResult.COMPATIBLE, result)
    }

    // ── Rollback preferences (NovelFeaturePreferences) ───────────────────────

    @Test
    fun `lastKnownGoodPluginVersion defaults to null when not set`() {
        val prefs = createPreferences()

        val stored = prefs.lastKnownGoodPluginVersionCode().get()

        assertNull(stored)
    }

    @Test
    fun `lastKnownGoodPluginVersion round-trips a stored version code`() {
        val prefs = createPreferences()
        val pref = prefs.lastKnownGoodPluginVersionCode()

        pref.set(42L)
        val stored = pref.get()

        assertEquals(42L, stored)
    }

    // ── PluginUpdatePolicyEvaluator integration ───────────────────────────────

    @Test
    fun `evaluator blocks plugin below minimum version`() {
        val evaluator = PluginUpdatePolicyEvaluator(
            minPluginVersionCode = 100L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 50L,
        )

        assertFalse(result.isAllowed)
        assertEquals(PolicyBlockReason.PLUGIN_TOO_OLD, result.blockReason)
    }

    @Test
    fun `evaluator allows plugin that passes all checks`() {
        val evaluator = PluginUpdatePolicyEvaluator(
            minPluginVersionCode = 100L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 100L,
        )

        assertTrue(result.isAllowed)
        assertNull(result.blockReason)
    }

    @Test
    fun `evaluator blocks old plugin even when other fields would be acceptable`() {
        val evaluator = PluginUpdatePolicyEvaluator(
            minPluginVersionCode = 100L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 10L,
        )

        assertFalse(result.isAllowed)
        assertEquals(PolicyBlockReason.PLUGIN_TOO_OLD, result.blockReason)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun createPreferences(): NovelFeaturePreferences =
        NovelFeaturePreferences(InMemoryPreferenceStore())
}
