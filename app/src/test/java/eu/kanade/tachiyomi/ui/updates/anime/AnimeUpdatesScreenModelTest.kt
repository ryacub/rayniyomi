package eu.kanade.tachiyomi.ui.updates.anime

import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.presentation.updates.anime.AnimeUpdatesUiModel
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.awaitAssert
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeUpdatesScreenModelTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `empty preferences pass empty lists to the interactor and render items`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        val interactorCalls = env.stubInteractor(flowOf(listOf(update(10))))

        val model = env.model()

        awaitAssert({ model.state.value.items }) { it.isNotEmpty() }

        assertEquals(1, model.state.value.items.size)
        assertEquals(emptyList<Long>() to emptyList<Long>(), interactorCalls.last())
    }

    @Test
    fun `cycleCategory cycles disabled to included to excluded to cleared`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        env.stubInteractor(flowOf(emptyList()))
        val model = env.model()
        awaitAssert({ model.state.value.isLoading }) { !it }
        val horror = category(id = 1, name = "Horror")

        model.cycleCategory(horror)
        assertEquals(setOf("1"), env.libraryPreferences.filterAnimeUpdatesCategories().get())
        assertEquals(emptySet<String>(), env.libraryPreferences.filterAnimeUpdatesCategoriesExclude().get())

        model.cycleCategory(horror)
        assertEquals(emptySet<String>(), env.libraryPreferences.filterAnimeUpdatesCategories().get())
        assertEquals(setOf("1"), env.libraryPreferences.filterAnimeUpdatesCategoriesExclude().get())

        model.cycleCategory(horror)
        assertEquals(emptySet<String>(), env.libraryPreferences.filterAnimeUpdatesCategories().get())
        assertEquals(emptySet<String>(), env.libraryPreferences.filterAnimeUpdatesCategoriesExclude().get())
    }

    @Test
    fun `preference change re-subscribes with the category id as long`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        val interactorCalls = env.stubInteractor(flowOf(emptyList()))
        val model = env.model()
        awaitAssert({ interactorCalls.toList() }) { it.isNotEmpty() }

        model.cycleCategory(category(id = 7, name = "Action"))

        awaitAssert({ interactorCalls.toList() }) { calls -> calls.any { it.first == listOf(7L) } }
    }

    @Test
    fun `stale category id in preference still executes without exception`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        env.libraryPreferences.filterAnimeUpdatesCategories().set(setOf("999"))
        val interactorCalls = env.stubInteractor(flowOf(listOf(update(10))))

        val model = env.model()

        awaitAssert({ interactorCalls.toList() }) { calls -> calls.any { it.first == listOf(999L) } }
        awaitAssert({ model.state.value.items }) { it.isNotEmpty() }
    }

    @Test
    fun `date headers still emit when the list spans dates`() {
        val items = persistentListOf(
            AnimeUpdatesItem(
                update = update(10, dateFetch = 1_700_000_000_000L),
                downloadStateProvider = { AnimeDownload.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
            AnimeUpdatesItem(
                update = update(20, dateFetch = 1_700_259_200_000L),
                downloadStateProvider = { AnimeDownload.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
        )

        val uiModel = AnimeUpdatesScreenModel.State(isLoading = false, items = items).getUiModel()

        assertTrue(uiModel.filterIsInstance<AnimeUpdatesUiModel.Header>().isNotEmpty())
    }

    @Test
    fun `anime cycleCategory does not mutate manga preferences`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        env.libraryPreferences.filterMangaUpdatesCategories().set(setOf("5"))
        env.libraryPreferences.filterMangaUpdatesCategoriesExclude().set(setOf("6"))
        env.stubInteractor(flowOf(emptyList()))
        val model = env.model()
        awaitAssert({ model.state.value.isLoading }) { !it }

        model.cycleCategory(category(id = 1, name = "Horror"))

        assertEquals(setOf("5"), env.libraryPreferences.filterMangaUpdatesCategories().get())
        assertEquals(setOf("6"), env.libraryPreferences.filterMangaUpdatesCategoriesExclude().get())
    }

    private inner class TestEnvironment {
        val preferences = InMemoryPreferenceStore()
        val libraryPreferences = LibraryPreferences(preferences)
        val downloadPreferences = DownloadPreferences(preferences)
        val getUpdates = mockk<GetAnimeUpdates>()
        val getCategories = mockk<GetAnimeCategories>()
        val downloadCache = mockk<AnimeDownloadCache>()
        val downloadManager = mockk<AnimeDownloadManager>()

        init {
            every { downloadCache.changes } returns MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
            every { downloadManager.queueState } returns MutableStateFlow(emptyList<AnimeDownload>())
            every { downloadManager.statusFlow() } returns emptyFlow()
            every { downloadManager.progressFlow() } returns emptyFlow()
            every { downloadManager.getQueuedDownloadOrNull(any()) } returns null
            every { downloadManager.isEpisodeDownloaded(any(), any(), any(), any()) } returns false
            every { getCategories.subscribe() } returns MutableStateFlow(
                listOf(category(id = 0, name = "")),
            )
        }

        fun stubInteractor(
            result: Flow<List<AnimeUpdatesWithRelations>>,
        ): CopyOnWriteArrayList<Pair<List<Long>, List<Long>>> {
            val calls = CopyOnWriteArrayList<Pair<List<Long>, List<Long>>>()
            every { getUpdates.subscribe(any(), any(), any()) } answers {
                val included = secondArg<List<Long>>()
                val excluded = thirdArg<List<Long>>()
                calls += included to excluded
                result
            }
            return calls
        }

        fun model(): AnimeUpdatesScreenModel {
            return AnimeUpdatesScreenModel(
                sourceManager = mockk<AnimeSourceManager>(relaxed = true),
                downloadManager = downloadManager,
                downloadCache = downloadCache,
                updateEpisode = mockk<UpdateEpisode>(relaxed = true),
                setSeenStatus = mockk<SetSeenStatus>(relaxed = true),
                getUpdates = getUpdates,
                getAnime = mockk<GetAnime>(relaxed = true),
                getEpisode = mockk<GetEpisode>(relaxed = true),
                libraryPreferences = libraryPreferences,
                downloadPreferences = downloadPreferences,
                getCategories = getCategories,
                ioDispatcher = vt.io,
            )
        }
    }

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        order = 0,
        flags = 0,
        hidden = false,
        parentId = null,
    )

    private fun update(episodeId: Long, dateFetch: Long = 1_000L) = AnimeUpdatesWithRelations(
        animeId = 1,
        animeTitle = "Anime",
        episodeId = episodeId,
        episodeName = "Episode $episodeId",
        scanlator = null,
        seen = false,
        bookmark = false,
        fillermark = false,
        lastSecondSeen = 0,
        totalSeconds = 0,
        sourceId = 1,
        dateFetch = dateFetch,
        coverData = AnimeCover(
            animeId = 1,
            sourceId = 1,
            isAnimeFavorite = true,
            url = null,
            lastModified = 0,
        ),
    )
}
