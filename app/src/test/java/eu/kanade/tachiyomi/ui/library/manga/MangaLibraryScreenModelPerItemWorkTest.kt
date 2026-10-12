package eu.kanade.tachiyomi.ui.library.manga

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.source.MangaSource
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
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.model.MangaTrack
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MangaLibraryScreenModelPerItemWorkTest {

    private val vt = VirtualTime()

    private val category = Category(id = 0L, name = "Default", order = 0L, flags = 0L, hidden = false)
    private val libraryMangas = (1L..ITEM_COUNT).map { id ->
        LibraryManga(
            manga = Manga.create().copy(id = id, title = "Title $id"),
            category = 0L,
            totalChapters = 10L,
            readCount = 0L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private val downloadChanges = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
    private val downloadCountCalls = AtomicInteger()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.sourcePreferences } returns SourcePreferences(InMemoryPreferenceStore(emptySequence()))
        val source = mockk<MangaSource>()
        every { source.name } returns "TestSource"
        every { source.lang } returns "en"
        val sourceManager = mockk<MangaSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source
        every { testAppGraph.mangaSourceManager } returns sourceManager
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `one download cache change filters the library once`() = runTest(vt.scheduler) {
        val model = createModel(
            prefs = listOf(pref("pref_filter_library_downloaded_v2", TriState.ENABLED_IS, TriState.DISABLED)),
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
            libraryMangas
                .filter { it.id % 2 == 0L }
                .associate { it.id to listOf(track(it.id)) },
        )
        val model = createModel(
            prefs = listOf(
                pref("pref_filter_library_tracked_${TRACKER_ID}_v2", TriState.ENABLED_IS, TriState.DISABLED),
            ),
            tracks = tracks,
            trackers = listOf(tracker),
        )

        awaitAssert({ model.state.value.library[category].orEmpty() }) { items ->
            items.map { it.libraryManga.id }.toSet() == (2L..ITEM_COUNT step 2).toSet()
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

    private fun track(mangaId: Long) = MangaTrack(
        id = mangaId,
        mangaId = mangaId,
        trackerId = TRACKER_ID,
        remoteId = 0L,
        libraryId = null,
        title = "",
        lastChapterRead = 0.0,
        totalChapters = 0L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    private fun createModel(
        prefs: List<InMemoryPreferenceStore.InMemoryPreference<*>> = emptyList(),
        tracks: Map<Long, List<MangaTrack>> = emptyMap(),
        trackers: List<BaseTracker> = emptyList(),
    ): MangaLibraryScreenModel {
        val getLibraryManga = mockk<GetLibraryManga>()
        every { getLibraryManga.subscribe(any()) } returns flowOf(libraryMangas)

        val getCategories = mockk<GetVisibleMangaCategories>()
        every { getCategories.subscribe() } returns flowOf(listOf(category))

        val getTracksPerManga = mockk<GetTracksPerManga>()
        every { getTracksPerManga.subscribe() } returns flowOf(tracks)

        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(trackers)
        every { trackerManager.getAll(any()) } returns emptyList()

        val downloadCache = mockk<MangaDownloadCache>()
        every { downloadCache.changes } returns downloadChanges

        val downloadManager = mockk<MangaDownloadManager>()
        every { downloadManager.getDownloadCount(any()) } answers {
            downloadCountCalls.incrementAndGet()
            1
        }

        val store = InMemoryPreferenceStore(prefs.asSequence())
        return MangaLibraryScreenModel(
            getLibraryManga = getLibraryManga,
            getCategories = getCategories,
            getTracksPerManga = getTracksPerManga,
            getNextChapters = mockk<GetNextChapters>(relaxed = true),
            getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true),
            setReadStatus = mockk<SetReadStatus>(relaxed = true),
            updateManga = mockk<UpdateManga>(relaxed = true),
            setMangaCategories = mockk<SetMangaCategories>(relaxed = true),
            preferences = BasePreferences(mockk<Context>(relaxed = true), store),
            libraryPreferences = LibraryPreferences(store),
            coverCache = mockk<MangaCoverCache>(relaxed = true),
            sourceManager = mockk<MangaSourceManager>(relaxed = true),
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
