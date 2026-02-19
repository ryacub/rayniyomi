package eu.kanade.tachiyomi.feature.novel

/**
 * Represents the network resolution state of the light novel plugin manifest.
 *
 * - [Online]: A fresh manifest was successfully fetched (or a valid fresh cache was used).
 * - [Degraded]: The network fetch failed but a stale cached manifest is available. The UI
 *   should surface a warning indicating the cache age so the user understands they may be
 *   seeing out-of-date information.
 * - [Offline]: No cache exists and every network attempt failed. The feature must be
 *   treated as unavailable and the user should be notified.
 */
internal sealed interface PluginNetworkState {

    /** A usable, fresh manifest is available. */
    data class Online(
        val manifest: LightNovelPluginManifest,
    ) : PluginNetworkState

    /**
     * The network fetch failed but a stale cached manifest was found.
     *
     * @param manifest The stale manifest from the cache.
     * @param cacheAgeMs How many milliseconds old the cached entry is.
     */
    data class Degraded(
        val manifest: LightNovelPluginManifest,
        val cacheAgeMs: Long,
    ) : PluginNetworkState

    /** No manifest is available — neither from network nor from cache. */
    data object Offline : PluginNetworkState
}
