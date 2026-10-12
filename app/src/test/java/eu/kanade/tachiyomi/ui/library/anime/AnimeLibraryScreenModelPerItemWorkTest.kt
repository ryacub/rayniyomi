package eu.kanade.tachiyomi.ui.library.anime

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.awaitAssert
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.model.AnimeTrack
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeLibraryScreenModelPerItemWorkTest {

    private val vt = VirtualTime()

    private val category = Category(id = 0L, name = "Default", order = 0L, flags = 0L, hidden = false)
    private val libraryAnimes = (1L..ITEM_COUNT).map { id ->
        LibraryAnime(
            anime = Anime.create().copy(id = id, title = "Title $id"),
            category = 0L,
            totalCount = 10L,
            seenCount = 0L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }

    private val downloadChanges = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    private val downloadCountCalls = AtomicInteger()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.sourcePreferences } returns SourcePreferences(InMemoryPreferenceStore(emptySequence()))
        val source = mockk<AnimeSource>()
        every { source.name } returns "TestSource"
        every { source.lang } returns "en"
        val sourceManager = mockk<AnimeSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source
        every { testAppGraph.animeSourceManager } returns sourceManager
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `one download cache change filters the library once`() = runTest(vt.scheduler) {
        val model = createModel(
            prefs = listOf(pref("pref_filter_animelib_downloaded_v2", TriState.ENABLED_IS, TriState.DISABLED)),
        )
        awaitAssert({ model.state.value.library[category].orEmpty() }) { it.size == ITEM_COUNT.toInt() }

        downloadCountCalls.set(0)
        downloadChanges.emit(Unit)
        advanceUntilIdle()

        assertEquals(ITEM_COUNT.toInt(), downloadCountCalls.get())
        assertEquals(ITEM_COUNT.toInt(), model.state.value.library[category].orEmpty().size)
    }

    @Test
    fun `tracker filter reads the track map once per pass, not once per item`() = runTest(vt.scheduler) {
        val tracker = mockk<BaseTracker>()
        every { tracker.id } returns TRACKER_ID
        val tracks = CountingMap(
            libraryAnimes
                .filter { it.id % 2 == 0L }
                .associate { it.id to listOf(track(it.id)) },
        )
        val model = createModel(
            prefs = listOf(
                pref("pref_filter_animelib_tracked_${TRACKER_ID}_v2", TriState.ENABLED_IS, TriState.DISABLED),
            ),
            tracks = tracks,
            trackers = listOf(tracker),
        )

        awaitAssert({ model.state.value.library[category].orEmpty() }) { items ->
            items.map { it.libraryAnime.id }.toSet() == (2L..ITEM_COUNT step 2).toSet()
        }
        assertTrue(tracks.entriesReads.get() < ITEM_COUNT) {
            "track map entries read ${tracks.entriesReads.get()} times for $ITEM_COUNT items"
        }
    }

    @Test
    fun `display mode state is created once`() = runTest(vt.scheduler) {
        val model = createModel()

        assertSame(model.getDisplayMode(), model.getDisplayMode())
    }

    @Test
    fun `column states are created once per orientation`() = runTest(vt.scheduler) {
        val model = createModel()

        assertSame(
            model.getColumnsPreferenceForCurrentOrientation(isLandscape = true),
            model.getColumnsPreferenceForCurrentOrientation(isLandscape = true),
        )
        assertSame(
            model.getColumnsPreferenceForCurrentOrientation(isLandscape = false),
            model.getColumnsPreferenceForCurrentOrientation(isLandscape = false),
        )
    }

    private class CountingMap<K, V>(private val delegate: Map<K, V>) : Map<K, V> by delegate {
        val entriesReads = AtomicInteger()
        override val entries: Set<Map.Entry<K, V>>
            get() {
                entriesReads.incrementAndGet()
                return delegate.entries
            }
    }

    private fun pref(key: String, value: TriState, default: TriState) =
        InMemoryPreferenceStore.InMemoryPreference(key = key, data = value, defaultValue = default)

    private fun track(animeId: Long) = AnimeTrack(
        id = animeId,
        animeId = animeId,
        trackerId = TRACKER_ID,
        remoteId = 0L,
        libraryId = null,
        title = "",
        lastEpisodeSeen = 0.0,
        totalEpisodes = 0L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    private fun createModel(
        prefs: List<InMemoryPreferenceStore.InMemoryPreference<*>> = emptyList(),
        tracks: Map<Long, List<AnimeTrack>> = emptyMap(),
        trackers: List<BaseTracker> = emptyList(),
    ): AnimeLibraryScreenModel {
        val getLibraryAnime = mockk<GetLibraryAnime>()
        every { getLibraryAnime.subscribe(any()) } returns flowOf(libraryAnimes)

        val getCategories = mockk<GetVisibleAnimeCategories>()
        every { getCategories.subscribe() } returns flowOf(listOf(category))

        val getTracksPerAnime = mockk<GetTracksPerAnime>()
        every { getTracksPerAnime.subscribe() } returns flowOf(tracks)

        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(trackers)
        every { trackerManager.getAll(any()) } returns emptyList()

        val downloadCache = mockk<AnimeDownloadCache>()
        every { downloadCache.changes } returns downloadChanges

        val downloadManager = mockk<AnimeDownloadManager>()
        every { downloadManager.getDownloadCount(any()) } answers {
            downloadCountCalls.incrementAndGet()
            1
        }

        val store = InMemoryPreferenceStore(prefs.asSequence())
        return AnimeLibraryScreenModel(
            getLibraryAnime = getLibraryAnime,
            getCategories = getCategories,
            getTracksPerAnime = getTracksPerAnime,
            getNextEpisodes = mockk<GetNextEpisodes>(relaxed = true),
            getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>(relaxed = true),
            setSeenStatus = mockk<SetSeenStatus>(relaxed = true),
            updateAnime = mockk<UpdateAnime>(relaxed = true),
            setAnimeCategories = mockk<SetAnimeCategories>(relaxed = true),
            preferences = BasePreferences(mockk<Context>(relaxed = true), store),
            libraryPreferences = LibraryPreferences(store),
            coverCache = mockk<AnimeCoverCache>(relaxed = true),
            backgroundCache = mockk<AnimeBackgroundCache>(relaxed = true),
            sourceManager = mockk<AnimeSourceManager>(relaxed = true),
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            trackerManager = trackerManager,
            ioDispatcher = vt.io,
        )
    }

    private companion object {
        const val ITEM_COUNT = 10L
        const val TRACKER_ID = 1L
    }
}
