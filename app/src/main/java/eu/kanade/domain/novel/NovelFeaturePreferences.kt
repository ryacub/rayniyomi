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

    // -----------------------------------------------------------------------------------------
    // Release channel & rollback (R236-J)
    // -----------------------------------------------------------------------------------------

    /**
     * The user's chosen [ReleaseChannel] for the light novel plugin.
     *
     * Stored as the enum name string so it survives app upgrades that add new
     * channels. Unknown values are silently coerced to [ReleaseChannel.STABLE].
     */
    public fun releaseChannel(): MappedPreference<ReleaseChannel> {
        val raw = preferenceStore.getString(KEY_RELEASE_CHANNEL, ReleaseChannel.STABLE.name)
        return MappedPreference(
            rawPreference = raw,
            toMapped = { ReleaseChannel.fromString(it) },
            fromMapped = { it.name },
        )
    }

    /**
     * The version code of the last successfully validated plugin build.
     *
     * Stored as a [Long] string. `null` (default) means no good version has
     * been pinned yet. The host writes this after every successful plugin load
     * and reads it to restore a known-good state during rollback.
     */
    public fun lastKnownGoodPluginVersionCode(): NullableLongPreference {
        val raw = preferenceStore.getString(KEY_LAST_KNOWN_GOOD_VERSION_CODE, "")
        return NullableLongPreference(raw)
    }

    public companion object {
        public const val CHANNEL_STABLE: String = "stable"
        public const val CHANNEL_BETA: String = "beta"

        /** 24 hours in milliseconds. */
        public const val DEFAULT_MANIFEST_TTL_MS: Long = 24L * 60L * 60L * 1_000L

        internal const val KEY_RELEASE_CHANNEL = "novel_plugin_release_channel"
        internal const val KEY_LAST_KNOWN_GOOD_VERSION_CODE = "novel_plugin_last_known_good_version"
    }
}
