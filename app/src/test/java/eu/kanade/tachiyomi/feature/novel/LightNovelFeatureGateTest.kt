package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class LightNovelFeatureGateTest {

    @Test
    fun `feature unavailable when toggle is off even if plugin is ready`() {
        val prefs = createPreferences(enabled = false)
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = true
            },
        )

        assertFalse(gate.isFeatureAvailable())
    }

    @Test
    fun `feature unavailable when toggle is on but plugin is not ready`() {
        val prefs = createPreferences(enabled = true)
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = false
            },
        )

        assertFalse(gate.isFeatureAvailable())
    }

    @Test
    fun `feature unavailable when plugin signer is invalid`() {
        val prefs = createPreferences(enabled = true)
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = false
            },
        )

        assertFalse(gate.isFeatureAvailable())
    }

    @Test
    fun `feature unavailable when plugin compatibility fails`() {
        val prefs = createPreferences(enabled = true)
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = false
            },
        )

        assertFalse(gate.isFeatureAvailable())
    }

    @Test
    fun `feature available when toggle is on and plugin is ready`() {
        val prefs = createPreferences(enabled = true)
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = true
            },
        )

        assertTrue(gate.isFeatureAvailable())
    }

    private fun createPreferences(enabled: Boolean): NovelFeaturePreferences {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "enable_light_novels",
                    data = enabled,
                    defaultValue = false,
                ),
            ),
        )
        return NovelFeaturePreferences(store)
    }
}
