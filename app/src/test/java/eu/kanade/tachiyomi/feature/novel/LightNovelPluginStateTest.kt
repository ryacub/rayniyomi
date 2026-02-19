package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for [PluginLifecycleState] transitions and the
 * [LightNovelPluginManager.resolveLifecycleState] helper that determines safe plugin states
 * based on installation presence and data integrity.
 *
 * These tests cover:
 * - Uninstall detection → [PluginLifecycleState.Uninstalled] (no crash)
 * - Corrupt data → [PluginLifecycleState.Corrupted]
 * - Healthy data + installed → [PluginLifecycleState.Ready]
 */
class LightNovelPluginStateTest {

    // --- Uninstall detection ---

    @Test
    fun `resolveLifecycleState returns Uninstalled when plugin is not installed`() {
        val state = resolveLifecycleState(
            isInstalled = false,
            dataIntegrity = PluginDataIntegrity.Ok,
        )

        assertEquals(PluginLifecycleState.Uninstalled, state)
    }

    @Test
    fun `resolveLifecycleState returns Uninstalled without throwing even if called repeatedly`() {
        repeat(5) {
            val state = resolveLifecycleState(
                isInstalled = false,
                dataIntegrity = PluginDataIntegrity.Ok,
            )
            assertEquals(PluginLifecycleState.Uninstalled, state)
        }
    }

    @Test
    fun `Uninstalled state is not considered ready`() {
        assertFalse(PluginLifecycleState.Uninstalled.isReady)
    }

    // --- Corruption detection ---

    @Test
    fun `resolveLifecycleState returns Corrupted when data is corrupt even if plugin is installed`() {
        val state = resolveLifecycleState(
            isInstalled = true,
            dataIntegrity = PluginDataIntegrity.Corrupt(reason = "SerializationException"),
        )

        assertTrue(state is PluginLifecycleState.Corrupted)
    }

    @Test
    fun `Corrupted state holds the corruption reason`() {
        val state = resolveLifecycleState(
            isInstalled = true,
            dataIntegrity = PluginDataIntegrity.Corrupt(reason = "illegal state"),
        )

        state as PluginLifecycleState.Corrupted
        assertEquals("illegal state", state.reason)
    }

    @Test
    fun `Corrupted state is not considered ready`() {
        val state = PluginLifecycleState.Corrupted(reason = "bad json")
        assertFalse(state.isReady)
    }

    // --- Healthy state ---

    @Test
    fun `resolveLifecycleState returns Ready when installed and data is healthy`() {
        val state = resolveLifecycleState(
            isInstalled = true,
            dataIntegrity = PluginDataIntegrity.Ok,
        )

        assertEquals(PluginLifecycleState.Ready, state)
    }

    @Test
    fun `Ready state isReady returns true`() {
        assertTrue(PluginLifecycleState.Ready.isReady)
    }

    // --- Reinstall path: transition from Uninstalled → Ready when reinstalled ---

    @Test
    fun `resolveLifecycleState returns Ready after reinstall when data is healthy`() {
        // Simulate: plugin was uninstalled, then reinstalled
        val afterUninstall = resolveLifecycleState(
            isInstalled = false,
            dataIntegrity = PluginDataIntegrity.Ok,
        )
        assertEquals(PluginLifecycleState.Uninstalled, afterUninstall)

        val afterReinstall = resolveLifecycleState(
            isInstalled = true,
            dataIntegrity = PluginDataIntegrity.Ok,
        )
        assertEquals(PluginLifecycleState.Ready, afterReinstall)
    }

    @Test
    fun `resolveLifecycleState returns Corrupted after reinstall when data is corrupt`() {
        val afterReinstall = resolveLifecycleState(
            isInstalled = true,
            dataIntegrity = PluginDataIntegrity.Corrupt(reason = "json parse error"),
        )

        assertTrue(afterReinstall is PluginLifecycleState.Corrupted)
    }

    // --- Uninstalled + corrupt data: uninstalled takes priority ---

    @Test
    fun `resolveLifecycleState returns Uninstalled when not installed regardless of data integrity`() {
        val state = resolveLifecycleState(
            isInstalled = false,
            dataIntegrity = PluginDataIntegrity.Corrupt(reason = "stale data"),
        )

        assertEquals(PluginLifecycleState.Uninstalled, state)
    }
}
