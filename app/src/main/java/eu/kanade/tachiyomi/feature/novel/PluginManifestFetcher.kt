package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Fetches the light novel plugin manifest with a TTL-based cache and exponential-backoff retry.
 *
 * The caller provides [httpFetch] as a suspend lambda so the class remains testable without
 * an Android runtime or a live network. Production callers inject an OkHttp-backed implementation;
 * tests inject a fake.
 *
 * Retry behaviour:
 * - Maximum 3 retries (4 total attempts) after the initial failure.
 * - Default delays between retries: 1 s → 2 s → 4 s (exponential backoff).
 * - Each attempt has its own 10 s timeout enforced at the call site by the HTTP client.
 *
 * Resolution logic (in order):
 * 1. If the cache is fresh (within TTL) → return [PluginNetworkState.Online] immediately.
 * 2. If the cache is stale or absent → attempt to fetch from the network with retries.
 *    a. Success → persist the new manifest to the cache, return [PluginNetworkState.Online].
 *    b. All retries exhausted WITH a stale cache → return [PluginNetworkState.Degraded].
 *    c. All retries exhausted WITHOUT any cache → return [PluginNetworkState.Offline].
 *
 * @param preferences Preference store that holds the manifest cache and TTL.
 * @param json Kotlinx serialisation instance for encoding/decoding the manifest.
 * @param httpFetch Suspend function that performs a single HTTP GET for the given URL and
 *   returns the raw response body on success or a [Throwable] on failure.
 * @param clock Returns the current wall-clock time in epoch milliseconds. Defaults to
 *   [System.currentTimeMillis]; override in tests for deterministic behaviour.
 * @param retryDelays Ordered list of delay durations (in milliseconds) before each successive
 *   retry. Defaults to [DEFAULT_RETRY_DELAYS_MS]. Tests pass shorter delays to keep fast.
 */
public class PluginManifestFetcher(
    private val preferences: NovelFeaturePreferences,
    private val json: Json,
    private val httpFetch: suspend (url: String) -> Result<String>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryDelays: List<Long> = DEFAULT_RETRY_DELAYS_MS,
) {
    /**
     * Resolves the plugin manifest at [url] following the cache + retry strategy described in the
     * class-level KDoc.
     *
     * This is a suspend function and must be called from a coroutine with an appropriate
     * dispatcher (e.g. [kotlinx.coroutines.Dispatchers.IO]).
     */
    public suspend fun fetchNetworkState(url: String): PluginNetworkState {
        val now = clock()

        // 1. Serve from cache if still fresh.
        if (!preferences.isCacheStale(clock = { now })) {
            val cached = preferences.cachedManifestJson().get()
            if (cached.isNotEmpty()) {
                val manifest = runCatching { json.decodeFromString<LightNovelPluginManifest>(cached) }
                    .getOrNull()
                if (manifest != null) {
                    logcat { "PluginManifestFetcher: serving fresh cached manifest" }
                    return PluginNetworkState.Online(manifest)
                } else {
                    logcat(LogPriority.WARN) {
                        "PluginManifestFetcher: fresh cache present but manifest failed to decode — falling through to network fetch"
                    }
                }
            }
        }

        // Capture stale cache details before attempting the network call.
        val staleCachedJson = preferences.cachedManifestJson().get()
        val staleCachedAt = preferences.manifestCachedAt().get()
        val staleManifest = staleCachedJson
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { json.decodeFromString<LightNovelPluginManifest>(it) }.getOrNull() }

        // 2. Attempt network fetch with retry/backoff.
        var lastError: Throwable? = null
        val totalAttempts = 1 + retryDelays.size
        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                val delayMs = retryDelays[attempt - 1]
                logcat { "PluginManifestFetcher: retry $attempt after ${delayMs}ms" }
                delay(delayMs)
            }

            // Guard against httpFetch implementations that throw instead of returning Result.failure(...).
            val fetchResult = runCatching { httpFetch(url) }.getOrElse { Result.failure(it) }
            fetchResult
                .onSuccess { body ->
                    val manifest = runCatching {
                        json.decodeFromString<LightNovelPluginManifest>(body)
                    }.getOrElse { parseError ->
                        logcat(LogPriority.WARN, parseError) { "PluginManifestFetcher: parse failed" }
                        lastError = parseError
                        return@onSuccess
                    }

                    // Persist to cache.
                    // Capture post-fetch time; do not reuse 'now' snapshot from entry — retries may span several seconds.
                    val fetchedAt = clock()
                    preferences.cachedManifestJson().set(body)
                    preferences.manifestCachedAt().set(fetchedAt)
                    logcat { "PluginManifestFetcher: fetched manifest successfully (attempt ${attempt + 1})" }
                    return PluginNetworkState.Online(manifest)
                }
                .onFailure { error ->
                    logcat(LogPriority.WARN, error) {
                        "PluginManifestFetcher: attempt ${attempt + 1} failed"
                    }
                    lastError = error
                }
        }

        // All attempts failed.
        return if (staleManifest != null) {
            val cacheAgeMs = now - staleCachedAt
            logcat(LogPriority.WARN) {
                "PluginManifestFetcher: all retries exhausted, serving stale cache (${cacheAgeMs}ms old)"
            }
            PluginNetworkState.Degraded(manifest = staleManifest, cacheAgeMs = cacheAgeMs)
        } else {
            logcat(LogPriority.ERROR, lastError) {
                "PluginManifestFetcher: all retries exhausted, no cache available"
            }
            PluginNetworkState.Offline
        }
    }

    public companion object {
        /** Default exponential back-off delays: 1 s → 2 s → 4 s (3 retries total). */
        public val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(1_000L, 2_000L, 4_000L)
    }
}
