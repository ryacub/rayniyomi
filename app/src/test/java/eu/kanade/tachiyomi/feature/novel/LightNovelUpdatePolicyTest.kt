package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.domain.novel.ReleaseChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Tests for plugin update policy: min-version enforcement, channel filtering, and rollback.
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

    // ── Channel filtering (evaluateChannelPolicy) ────────────────────────────

    @Test
    fun `evaluateChannelPolicy allows stable plugin on stable channel`() {
        val result = evaluateChannelPolicy(
            pluginChannel = ReleaseChannel.STABLE,
            hostChannel = ReleaseChannel.STABLE,
        )

        assertEquals(ChannelPolicyResult.ALLOWED, result)
    }

    @Test
    fun `evaluateChannelPolicy allows beta plugin on beta channel`() {
        val result = evaluateChannelPolicy(
            pluginChannel = ReleaseChannel.BETA,
            hostChannel = ReleaseChannel.BETA,
        )

        assertEquals(ChannelPolicyResult.ALLOWED, result)
    }

    @Test
    fun `evaluateChannelPolicy allows stable plugin on beta channel`() {
        // Stable plugins are always accepted, regardless of host channel
        val result = evaluateChannelPolicy(
            pluginChannel = ReleaseChannel.STABLE,
            hostChannel = ReleaseChannel.BETA,
        )

        assertEquals(ChannelPolicyResult.ALLOWED, result)
    }

    @Test
    fun `evaluateChannelPolicy blocks beta plugin when host channel is stable`() {
        val result = evaluateChannelPolicy(
            pluginChannel = ReleaseChannel.BETA,
            hostChannel = ReleaseChannel.STABLE,
        )

        assertEquals(ChannelPolicyResult.BLOCKED_WRONG_CHANNEL, result)
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

    @Test
    fun `releaseChannel preference defaults to STABLE`() {
        val prefs = createPreferences()

        val channel = prefs.releaseChannel().get()

        assertEquals(ReleaseChannel.STABLE, channel)
    }

    @Test
    fun `releaseChannel preference round-trips BETA`() {
        val prefs = createPreferences()
        val pref = prefs.releaseChannel()

        pref.set(ReleaseChannel.BETA)
        val stored = pref.get()

        assertEquals(ReleaseChannel.BETA, stored)
    }

    // ── PluginUpdatePolicyEvaluator integration ───────────────────────────────

    @Test
    fun `evaluator blocks plugin below minimum version regardless of channel`() {
        val evaluator = PluginUpdatePolicyEvaluator(
            hostChannel = ReleaseChannel.STABLE,
            minPluginVersionCode = 100L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 50L,
            pluginChannel = ReleaseChannel.STABLE,
        )

        assertFalse(result.isAllowed)
        assertEquals(PolicyBlockReason.PLUGIN_TOO_OLD, result.blockReason)
    }

    @Test
    fun `evaluator blocks beta plugin when host is on stable channel`() {
        val evaluator = PluginUpdatePolicyEvaluator(
            hostChannel = ReleaseChannel.STABLE,
            minPluginVersionCode = 1L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 200L,
            pluginChannel = ReleaseChannel.BETA,
        )

        assertFalse(result.isAllowed)
        assertEquals(PolicyBlockReason.WRONG_CHANNEL, result.blockReason)
    }

    @Test
    fun `evaluator allows plugin that passes all checks`() {
        val evaluator = PluginUpdatePolicyEvaluator(
            hostChannel = ReleaseChannel.STABLE,
            minPluginVersionCode = 100L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 100L,
            pluginChannel = ReleaseChannel.STABLE,
        )

        assertTrue(result.isAllowed)
        assertNull(result.blockReason)
    }

    @Test
    fun `evaluator version check takes priority over channel check`() {
        // When both version and channel are bad, PLUGIN_TOO_OLD is returned first
        val evaluator = PluginUpdatePolicyEvaluator(
            hostChannel = ReleaseChannel.STABLE,
            minPluginVersionCode = 100L,
        )

        val result = evaluator.evaluate(
            pluginVersionCode = 10L,
            pluginChannel = ReleaseChannel.BETA,
        )

        assertFalse(result.isAllowed)
        assertEquals(PolicyBlockReason.PLUGIN_TOO_OLD, result.blockReason)
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private fun createPreferences(): NovelFeaturePreferences =
        NovelFeaturePreferences(InMemoryPreferenceStore())
}
