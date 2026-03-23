package eu.kanade.domain.novel

import tachiyomi.core.common.preference.Preference
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

    /**
     * The version code of the last successfully validated plugin build.
     *
     * Stored as a [Long] string. `null` (default) means no good version has
     * been pinned yet. The host writes this after every successful plugin load
     * and reads it to restore a known-good state during rollback.
     */
    public fun lastKnownGoodPluginVersionCode(): Preference<Long?> =
        preferenceStore.getObject(
            key = KEY_LAST_KNOWN_GOOD_VERSION_CODE,
            defaultValue = null,
            serializer = { it?.toString() ?: "" },
            deserializer = { it.toLongOrNull() },
        )

    public companion object {
        public const val CHANNEL_STABLE: String = "stable"

        /** 24 hours in milliseconds. */
        public const val DEFAULT_MANIFEST_TTL_MS: Long = 24L * 60L * 60L * 1_000L

        internal const val KEY_LAST_KNOWN_GOOD_VERSION_CODE = "novel_plugin_last_known_good_version"
    }
}
