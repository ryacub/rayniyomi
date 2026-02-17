package eu.kanade.domain.novel

import tachiyomi.core.common.preference.PreferenceStore

class NovelFeaturePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun enableLightNovels() = preferenceStore.getBoolean("enable_light_novels", false)

    fun lightNovelPluginChannel() = preferenceStore.getString("light_novel_plugin_channel", CHANNEL_STABLE)

    companion object {
        const val CHANNEL_STABLE = "stable"
        const val CHANNEL_BETA = "beta"
    }
}
