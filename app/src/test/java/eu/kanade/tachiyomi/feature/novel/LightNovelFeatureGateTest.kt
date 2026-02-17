package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class LightNovelFeatureGateTest {

    @Test
    fun `feature unavailable when toggle is off even if plugin is ready`() {
        val prefs = NovelFeaturePreferences(InMemoryPreferenceStore())
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = true
            },
        )

        prefs.enableLightNovels().set(false)

        assertFalse(gate.isFeatureAvailable())
    }

    @Test
    fun `feature unavailable when toggle is on but plugin is not ready`() {
        val prefs = NovelFeaturePreferences(InMemoryPreferenceStore())
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = false
            },
        )

        prefs.enableLightNovels().set(true)

        assertFalse(gate.isFeatureAvailable())
    }

    @Test
    fun `feature available when toggle is on and plugin is ready`() {
        val prefs = NovelFeaturePreferences(InMemoryPreferenceStore())
        val gate = LightNovelFeatureGate(
            preferences = prefs,
            pluginReadiness = object : LightNovelPluginReadiness {
                override fun isPluginReady() = true
            },
        )

        prefs.enableLightNovels().set(true)

        assertTrue(gate.isFeatureAvailable())
    }
}
