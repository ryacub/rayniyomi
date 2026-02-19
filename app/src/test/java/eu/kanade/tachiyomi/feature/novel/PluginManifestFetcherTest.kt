package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Unit tests for [PluginManifestFetcher] covering cache TTL and retry/degraded mode behaviour.
 *
 * TDD: these tests were written before the implementation classes existed.
 */
class PluginManifestFetcherTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleManifest = LightNovelPluginManifest(
        packageName = "xyz.rayniyomi.plugin.lightnovel",
        versionCode = 1L,
        pluginApiVersion = 1,
        minHostVersion = 1L,
        targetHostVersion = null,
        apkUrl = "https://example.com/plugin.apk",
        apkSha256 = "abc123",
    )

    private lateinit var preferences: NovelFeaturePreferences
    private lateinit var store: InMemoryPreferenceStore

    @BeforeEach
    fun setUp() {
        store = InMemoryPreferenceStore()
        preferences = NovelFeaturePreferences(store)
    }

    // -----------------------------------------------------------------------------------
    // Fresh cache — no network call expected
    // -----------------------------------------------------------------------------------

    @Test
    fun `fresh cache returns cached manifest without making a network call`() = runTest {
        val cachedJson = json.encodeToString(sampleManifest)
        val now = System.currentTimeMillis()
        val freshStore = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_json",
                    data = cachedJson,
                    defaultValue = "",
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_at",
                    data = now,
                    defaultValue = 0L,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_ttl_ms",
                    data = DEFAULT_TTL_MS,
                    defaultValue = DEFAULT_TTL_MS,
                ),
            ),
        )
        val freshPrefs = NovelFeaturePreferences(freshStore)
        var networkCallCount = 0
        val fetcher = PluginManifestFetcher(
            preferences = freshPrefs,
            json = json,
            httpFetch = { _ ->
                networkCallCount++
                Result.failure(AssertionError("Network should not be called for a fresh cache"))
            },
            clock = { now },
        )

        val state = fetcher.fetchNetworkState("https://example.com/manifest.json")

        state.shouldBeInstanceOf<PluginNetworkState.Online>()
        networkCallCount shouldBe 0
        (state as PluginNetworkState.Online).manifest.packageName shouldBe sampleManifest.packageName
    }

    // -----------------------------------------------------------------------------------
    // Stale cache + network success — cache updated, fresh manifest returned as Online
    // -----------------------------------------------------------------------------------

    @Test
    fun `stale cache with network success updates cache and returns Online`() = runTest {
        val cachedJson = json.encodeToString(sampleManifest)
        val staleTime = System.currentTimeMillis() - DEFAULT_TTL_MS - 1_000L
        val now = System.currentTimeMillis()
        val staleStore = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_json",
                    data = cachedJson,
                    defaultValue = "",
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_at",
                    data = staleTime,
                    defaultValue = 0L,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_ttl_ms",
                    data = DEFAULT_TTL_MS,
                    defaultValue = DEFAULT_TTL_MS,
                ),
            ),
        )
        val stalePrefs = NovelFeaturePreferences(staleStore)
        val updatedManifest = sampleManifest.copy(versionCode = 2L)
        val updatedJson = json.encodeToString(updatedManifest)
        // Track the values written to the cache preferences during the fetch.
        var writtenJson: String? = null
        var writtenCachedAt: Long? = null
        val spyStore = object : tachiyomi.core.common.preference.PreferenceStore by staleStore {
            override fun getString(key: String, defaultValue: String) =
                object : tachiyomi.core.common.preference.Preference<String>
                by staleStore.getString(key, defaultValue) {
                    override fun set(value: String) {
                        if (key == "novel_manifest_cached_json") writtenJson = value
                        staleStore.getString(key, defaultValue).set(value)
                    }
                }
            override fun getLong(key: String, defaultValue: Long) =
                object : tachiyomi.core.common.preference.Preference<Long>
                by staleStore.getLong(key, defaultValue) {
                    override fun set(value: Long) {
                        if (key == "novel_manifest_cached_at") writtenCachedAt = value
                        staleStore.getLong(key, defaultValue).set(value)
                    }
                }
        }
        val spyPrefs = NovelFeaturePreferences(spyStore)
        val fetcher = PluginManifestFetcher(
            preferences = spyPrefs,
            json = json,
            httpFetch = { _ -> Result.success(updatedJson) },
            clock = { now },
        )

        val state = fetcher.fetchNetworkState("https://example.com/manifest.json")

        // The fetcher must return the freshly-fetched manifest (not the stale one) as Online.
        state.shouldBeInstanceOf<PluginNetworkState.Online>()
        (state as PluginNetworkState.Online).manifest.versionCode shouldBe 2L
        // The updated manifest JSON must have been persisted to the cache.
        writtenJson shouldBe updatedJson
        // The cache timestamp must have been written (non-zero).
        writtenCachedAt shouldBe now
    }

    // -----------------------------------------------------------------------------------
    // Stale cache + network failure — Degraded state with stale manifest
    // -----------------------------------------------------------------------------------

    @Test
    fun `stale cache with network failure returns Degraded state`() = runTest {
        val cachedJson = json.encodeToString(sampleManifest)
        val staleTime = System.currentTimeMillis() - DEFAULT_TTL_MS - 1_000L
        val now = System.currentTimeMillis()
        val staleStore = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_json",
                    data = cachedJson,
                    defaultValue = "",
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_at",
                    data = staleTime,
                    defaultValue = 0L,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_ttl_ms",
                    data = DEFAULT_TTL_MS,
                    defaultValue = DEFAULT_TTL_MS,
                ),
            ),
        )
        val stalePrefs = NovelFeaturePreferences(staleStore)
        val fetcher = PluginManifestFetcher(
            preferences = stalePrefs,
            json = json,
            httpFetch = { _ -> Result.failure(RuntimeException("Network error")) },
            clock = { now },
        )

        val state = fetcher.fetchNetworkState("https://example.com/manifest.json")

        state.shouldBeInstanceOf<PluginNetworkState.Degraded>()
        val degraded = state as PluginNetworkState.Degraded
        degraded.manifest.packageName shouldBe sampleManifest.packageName
        degraded.cacheAgeMs shouldBe (now - staleTime)
    }

    // -----------------------------------------------------------------------------------
    // No cache + network failure — Offline after all retries exhausted
    // -----------------------------------------------------------------------------------

    @Test
    fun `no cache with network failure returns Offline after retries exhausted`() = runTest {
        val emptyStore = InMemoryPreferenceStore()
        val emptyPrefs = NovelFeaturePreferences(emptyStore)
        var callCount = 0
        val fetcher = PluginManifestFetcher(
            preferences = emptyPrefs,
            json = json,
            httpFetch = { _ ->
                callCount++
                Result.failure(RuntimeException("Network error attempt $callCount"))
            },
            clock = { System.currentTimeMillis() },
            retryDelays = listOf(0L, 0L, 0L), // zero delays for fast tests
        )

        val state = fetcher.fetchNetworkState("https://example.com/manifest.json")

        state.shouldBeInstanceOf<PluginNetworkState.Offline>()
        // initial attempt + 3 retries = 4 total
        callCount shouldBe 4
    }

    // -----------------------------------------------------------------------------------
    // isCacheStale helper
    // -----------------------------------------------------------------------------------

    @Test
    fun `isCacheStale returns true when no cache exists`() {
        val freshPrefs = NovelFeaturePreferences(InMemoryPreferenceStore())
        freshPrefs.isCacheStale(clock = { System.currentTimeMillis() }) shouldBe true
    }

    @Test
    fun `isCacheStale returns false when cache is within TTL`() {
        val now = System.currentTimeMillis()
        val freshStore = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_at",
                    data = now - 1_000L,
                    defaultValue = 0L,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_ttl_ms",
                    data = DEFAULT_TTL_MS,
                    defaultValue = DEFAULT_TTL_MS,
                ),
            ),
        )
        val freshPrefs = NovelFeaturePreferences(freshStore)
        freshPrefs.isCacheStale(clock = { now }) shouldBe false
    }

    @Test
    fun `isCacheStale returns true when cache is past TTL`() {
        val now = System.currentTimeMillis()
        val staleStore = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_cached_at",
                    data = now - DEFAULT_TTL_MS - 1L,
                    defaultValue = 0L,
                ),
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "novel_manifest_ttl_ms",
                    data = DEFAULT_TTL_MS,
                    defaultValue = DEFAULT_TTL_MS,
                ),
            ),
        )
        val stalePrefs = NovelFeaturePreferences(staleStore)
        stalePrefs.isCacheStale(clock = { now }) shouldBe true
    }

    private companion object {
        val DEFAULT_TTL_MS = NovelFeaturePreferences.DEFAULT_MANIFEST_TTL_MS
    }
}
