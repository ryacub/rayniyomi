package eu.kanade.domain.novel

import tachiyomi.core.common.preference.PreferenceStore

class NovelFeaturePreferences(
    private val preferenceStore: PreferenceStore,
) {
    private val enableLightNovelsPref = preferenceStore.getBoolean("enable_light_novels", false)
    private val lightNovelPluginChannelPref = preferenceStore.getString("light_novel_plugin_channel", CHANNEL_STABLE)

    fun enableLightNovels() = enableLightNovelsPref

    fun lightNovelPluginChannel() = lightNovelPluginChannelPref

    companion object {
        const val CHANNEL_STABLE = "stable"
        const val CHANNEL_BETA = "beta"
    }
}
