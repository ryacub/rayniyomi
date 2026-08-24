package eu.kanade.tachiyomi.ui.updates.manga

import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.presentation.updates.manga.MangaUpdatesUiModel
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
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
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class MangaUpdatesScreenModelTest {

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
        assertEquals(setOf("1"), env.libraryPreferences.filterMangaUpdatesCategories().get())
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpdatesCategoriesExclude().get())

        model.cycleCategory(horror)
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpdatesCategories().get())
        assertEquals(setOf("1"), env.libraryPreferences.filterMangaUpdatesCategoriesExclude().get())

        model.cycleCategory(horror)
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpdatesCategories().get())
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpdatesCategoriesExclude().get())
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
        env.libraryPreferences.filterMangaUpdatesCategories().set(setOf("999"))
        val interactorCalls = env.stubInteractor(flowOf(listOf(update(10))))

        val model = env.model()

        awaitAssert({ interactorCalls.toList() }) { calls -> calls.any { it.first == listOf(999L) } }
        awaitAssert({ model.state.value.items }) { it.isNotEmpty() }
    }

    @Test
    fun `date headers still emit when the list spans dates`() {
        val items = persistentListOf(
            MangaUpdatesItem(
                update = update(10, dateFetch = 1_700_000_000_000L),
                downloadStateProvider = { MangaDownload.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
            MangaUpdatesItem(
                update = update(20, dateFetch = 1_700_259_200_000L),
                downloadStateProvider = { MangaDownload.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
        )

        val uiModel = MangaUpdatesScreenModel.State(isLoading = false, items = items).getUiModel()

        assertTrue(uiModel.filterIsInstance<MangaUpdatesUiModel.Header>().isNotEmpty())
    }

    @Test
    fun `manga cycleCategory does not mutate anime preferences`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        env.libraryPreferences.filterAnimeUpdatesCategories().set(setOf("5"))
        env.libraryPreferences.filterAnimeUpdatesCategoriesExclude().set(setOf("6"))
        env.stubInteractor(flowOf(emptyList()))
        val model = env.model()
        awaitAssert({ model.state.value.isLoading }) { !it }

        model.cycleCategory(category(id = 1, name = "Horror"))

        assertEquals(setOf("5"), env.libraryPreferences.filterAnimeUpdatesCategories().get())
        assertEquals(setOf("6"), env.libraryPreferences.filterAnimeUpdatesCategoriesExclude().get())
    }

    private inner class TestEnvironment {
        val preferences = InMemoryPreferenceStore()
        val libraryPreferences = LibraryPreferences(preferences)
        val getUpdates = mockk<GetMangaUpdates>()
        val getCategories = mockk<GetMangaCategories>()
        val downloadCache = mockk<MangaDownloadCache>()
        val downloadManager = mockk<MangaDownloadManager>()

        init {
            every { downloadCache.changes } returns MutableSharedFlow<Unit>(replay = 1).also { it.tryEmit(Unit) }
            every { downloadManager.queueState } returns MutableStateFlow(emptyList<MangaDownload>())
            every { downloadManager.statusFlow() } returns emptyFlow()
            every { downloadManager.progressFlow() } returns emptyFlow()
            every { downloadManager.getQueuedDownloadOrNull(any()) } returns null
            every { downloadManager.isChapterDownloaded(any(), any(), any(), any()) } returns false
            every { getCategories.subscribe() } returns MutableStateFlow(
                listOf(category(id = 0, name = "")),
            )
        }

        fun stubInteractor(
            result: Flow<List<MangaUpdatesWithRelations>>,
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

        fun model(): MangaUpdatesScreenModel {
            return MangaUpdatesScreenModel(
                sourceManager = mockk<MangaSourceManager>(relaxed = true),
                downloadManager = downloadManager,
                downloadCache = downloadCache,
                updateChapter = mockk<UpdateChapter>(relaxed = true),
                setReadStatus = mockk<SetReadStatus>(relaxed = true),
                getUpdates = getUpdates,
                getManga = mockk<GetManga>(relaxed = true),
                getChapter = mockk<GetChapter>(relaxed = true),
                getCategories = getCategories,
                libraryPreferences = libraryPreferences,
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

    private fun update(chapterId: Long, dateFetch: Long = 1_000L) = MangaUpdatesWithRelations(
        mangaId = 1,
        mangaTitle = "Manga",
        chapterId = chapterId,
        chapterName = "Chapter $chapterId",
        scanlator = null,
        read = false,
        bookmark = false,
        lastPageRead = 0,
        sourceId = 1,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = 1,
            sourceId = 1,
            isMangaFavorite = true,
            url = null,
            lastModified = 0,
        ),
    )
}
