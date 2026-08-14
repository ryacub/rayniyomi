package eu.kanade.tachiyomi.ui.library.manga

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        Dispatchers.resetMain()
    }

    @Test
    fun `stale search evaluation cannot replace a newer query result`() = runBlocking {
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
        val category = Category(id = 0L, name = "Default", order = 0L, flags = 0L, hidden = false)

        val getLibraryManga = mockk<GetLibraryManga>()
        every { getLibraryManga.subscribe(any()) } returns flowOf(libraryMangas)

        val getCategories = mockk<GetVisibleMangaCategories>()
        every { getCategories.subscribe() } returns flowOf(listOf(category))

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
        val model = MangaLibraryScreenModel(
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
        )

        // Both searches land inside the SEARCH_DEBOUNCE_MILLIS window, so the debounce collapses
        // them into a single evaluation of the newer query. The synchronous filter inside the
        // combine/collectLatest transformer guarantees no stale "Alpha" evaluation can land after
        // the "Beta" result.
        model.search("Alpha")
        model.search("Beta")

        eventually(timeoutMs = 5_000) {
            val items = model.state.value.library[category].orEmpty()
            items.isNotEmpty() && items.all { it.libraryManga.manga.title == "Beta" }
        }
    }

    private suspend fun eventually(timeoutMs: Long, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }
}
