package eu.kanade.domain.novel

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preference accessors for the optional light novel feature.
 *
 * Cache fields added for R236-M (offline resilience):
 * - [cachedManifestJson]: raw JSON of the last successfully fetched manifest.
 * - [manifestCachedAt]: epoch-millis timestamp of the last successful fetch.
 */
public class NovelFeaturePreferences(
    private val preferenceStore: PreferenceStore,
) {
    public fun enableLightNovels() = preferenceStore.getBoolean("enable_light_novels", false)

    public fun lightNovelPluginChannel() = preferenceStore.getString(
        "light_novel_plugin_channel",
        CHANNEL_STABLE,
    )

    // -----------------------------------------------------------------------------------------
    // Manifest cache (R236-M)
    // -----------------------------------------------------------------------------------------

    /** Raw JSON string of the most recently fetched [eu.kanade.tachiyomi.feature.novel.LightNovelPluginManifest]. */
    public fun cachedManifestJson() = preferenceStore.getString(
        "novel_manifest_cached_json",
        defaultValue = "",
    )

    /** Epoch-millisecond timestamp when the cache was last written. 0 means never cached. */
    public fun manifestCachedAt() = preferenceStore.getLong(
        "novel_manifest_cached_at",
        defaultValue = 0L,
    )

    public companion object {
        public const val CHANNEL_STABLE: String = "stable"
        public const val CHANNEL_BETA: String = "beta"

        /** 24 hours in milliseconds. */
        public const val DEFAULT_MANIFEST_TTL_MS: Long = 24L * 60L * 60L * 1_000L
    }
}
