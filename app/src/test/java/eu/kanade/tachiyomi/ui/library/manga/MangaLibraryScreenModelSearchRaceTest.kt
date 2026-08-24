package eu.kanade.tachiyomi.ui.library.manga

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.awaitAssert
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

@OptIn(ExperimentalCoroutinesApi::class)
class MangaLibraryScreenModelSearchRaceTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        // The library item constructor resolves its sourceManager through Injekt, and the
        // real getNameForMangaInfo() extension resolves SourcePreferences through Injekt.
        Injekt.addSingleton(
            SourcePreferences(
                InMemoryPreferenceStore(
                    sequenceOf(
                        InMemoryPreferenceStore.InMemoryPreference(
                            key = "source_languages",
                            data = setOf("en"),
                            defaultValue = emptySet<String>(),
                        ),
                    ),
                ),
            ),
        )
        val source = mockk<MangaSource>()
        every { source.name } returns "TestSource"
        every { source.lang } returns "en"
        val sourceManager = mockk<MangaSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source
        Injekt.addSingleton(sourceManager)
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `search changes inside the debounce window collapse to the newer query`() = runTest(vt.scheduler) {
        val model = createModel()

        // Both searches land inside SEARCH_DEBOUNCE_MILLIS, so the debounce collapses them
        // into a single evaluation of the newer query. A stale "Alpha" evaluation never exists.
        model.search("Alpha")
        model.search("Beta")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryManga.manga.title == "Beta" }
        }
    }

    @Test
    fun `a spaced search result replaces the older evaluation`() = runTest(vt.scheduler) {
        val model = createModel()

        model.search("Alpha")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // Wait so the "Alpha" evaluation actually emits first.
        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryManga.manga.title == "Alpha" }
        }

        model.search("Beta")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // The newer query evaluation must replace the older one in the final state.
        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryManga.manga.title == "Beta" }
        }
    }

    @Test
    fun `a comparison query result replaces the older evaluation`() = runTest(vt.scheduler) {
        val model = createModel()

        model.search("Alpha")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryManga.manga.title == "Alpha" }
        }

        model.search("total>=1")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // Both entries have 10 chapters, so the comparison matches them both. The older
        // "Alpha" evaluation could never produce the Beta entry in the final state.
        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.size == 2 && items.any { it.libraryManga.manga.title == "Beta" }
        }
    }

    private fun createModel(): MangaLibraryScreenModel {
        val mangaA = Manga.create().copy(id = 1L, title = "Alpha")
        val mangaB = Manga.create().copy(id = 2L, title = "Beta")
        val libraryMangas = listOf(
            LibraryManga(
                manga = mangaA,
                category = 0L,
                totalChapters = 10L,
                readCount = 0L,
                bookmarkCount = 0L,
                latestUpload = 0L,
                chapterFetchedAt = 0L,
                lastRead = 0L,
            ),
            LibraryManga(
                manga = mangaB,
                category = 0L,
                totalChapters = 10L,
                readCount = 0L,
                bookmarkCount = 0L,
                latestUpload = 0L,
                chapterFetchedAt = 0L,
                lastRead = 0L,
            ),
        )

        val getLibraryManga = mockk<GetLibraryManga>()
        every { getLibraryManga.subscribe(any()) } returns flowOf(libraryMangas)

        val getCategories = mockk<GetVisibleMangaCategories>()
        every { getCategories.subscribe() } returns flowOf(listOf(category()))

        val getTracksPerManga = mockk<GetTracksPerManga>()
        every { getTracksPerManga.subscribe() } returns flowOf(emptyMap<Long, List<MangaTrack>>())

        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(emptyList())
        every { trackerManager.getAll(any()) } returns emptyList()

        val downloadCache = mockk<MangaDownloadCache>()
        val downloadChanges = MutableSharedFlow<Unit>(replay = 1)
        downloadChanges.tryEmit(Unit)
        every { downloadCache.changes } returns downloadChanges

        val downloadManager = mockk<MangaDownloadManager>()
        every { downloadManager.getDownloadCount(any()) } returns 0

        val store = InMemoryPreferenceStore(emptySequence())
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

    private fun category() = Category(id = 0L, name = "Default", order = 0L, flags = 0L, hidden = false)
}
