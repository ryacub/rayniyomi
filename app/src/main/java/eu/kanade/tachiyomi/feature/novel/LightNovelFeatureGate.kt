package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences

interface LightNovelPluginReadiness {
    fun isPluginReady(): Boolean
}

class LightNovelFeatureGate(
    private val preferences: NovelFeaturePreferences,
    private val pluginReadiness: LightNovelPluginReadiness,
) {
    fun isFeatureAvailable(): Boolean {
        return preferences.enableLightNovels().get() && pluginReadiness.isPluginReady()
    }
}
