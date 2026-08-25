package eu.kanade.tachiyomi.ui.library.anime

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeLibraryScreenModelSearchRaceTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        // The library item constructor resolves its sourceManager through Injekt, and the
        // real getNameForAnimeInfo() extension resolves SourcePreferences through Injekt.
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
        val source = mockk<AnimeSource>()
        every { source.name } returns "TestSource"
        every { source.lang } returns "en"
        val sourceManager = mockk<AnimeSourceManager>()
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
            items.isNotEmpty() && items.all { it.libraryAnime.anime.title == "Beta" }
        }
    }

    @Test
    fun `a spaced search result replaces the older evaluation`() = runTest(vt.scheduler) {
        val model = createModel()

        model.search("Alpha")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // Wait so the "Alpha" evaluation actually emits first.
        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryAnime.anime.title == "Alpha" }
        }

        model.search("Beta")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // The newer query evaluation must replace the older one in the final state.
        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryAnime.anime.title == "Beta" }
        }
    }

    @Test
    fun `a comparison query result replaces the older evaluation`() = runTest(vt.scheduler) {
        val model = createModel()

        model.search("Alpha")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.isNotEmpty() && items.all { it.libraryAnime.anime.title == "Alpha" }
        }

        model.search("total>=1")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // Both entries have 10 episodes, so the comparison matches them both. The older
        // "Alpha" evaluation could never produce the Beta entry in the final state.
        awaitAssert({ model.state.value.library[category()].orEmpty() }) { items ->
            items.size == 2 && items.any { it.libraryAnime.anime.title == "Beta" }
        }
    }

    private fun createModel(): AnimeLibraryScreenModel {
        val animeA = Anime.create().copy(id = 1L, title = "Alpha")
        val animeB = Anime.create().copy(id = 2L, title = "Beta")
        val libraryAnimes = listOf(
            LibraryAnime(
                anime = animeA,
                category = 0L,
                totalCount = 10L,
                seenCount = 0L,
                bookmarkCount = 0L,
                fillermarkCount = 0L,
                latestUpload = 0L,
                episodeFetchedAt = 0L,
                lastSeen = 0L,
            ),
            LibraryAnime(
                anime = animeB,
                category = 0L,
                totalCount = 10L,
                seenCount = 0L,
                bookmarkCount = 0L,
                fillermarkCount = 0L,
                latestUpload = 0L,
                episodeFetchedAt = 0L,
                lastSeen = 0L,
            ),
        )

        val getLibraryAnime = mockk<GetLibraryAnime>()
        every { getLibraryAnime.subscribe(any()) } returns flowOf(libraryAnimes)

        val getCategories = mockk<GetVisibleAnimeCategories>()
        every { getCategories.subscribe() } returns flowOf(listOf(category()))

        val getTracksPerAnime = mockk<GetTracksPerAnime>()
        every { getTracksPerAnime.subscribe() } returns flowOf(emptyMap<Long, List<AnimeTrack>>())

        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.loggedInTrackersFlow() } returns flowOf(emptyList())
        every { trackerManager.getAll(any()) } returns emptyList()

        val downloadCache = mockk<AnimeDownloadCache>()
        val downloadChanges = MutableSharedFlow<Unit>(replay = 1)
        downloadChanges.tryEmit(Unit)
        every { downloadCache.changes } returns downloadChanges

        val downloadManager = mockk<AnimeDownloadManager>()
        every { downloadManager.getDownloadCount(any()) } returns 0

        val store = InMemoryPreferenceStore(emptySequence())
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

    private fun category() = Category(id = 0L, name = "Default", order = 0L, flags = 0L, hidden = false)
}
