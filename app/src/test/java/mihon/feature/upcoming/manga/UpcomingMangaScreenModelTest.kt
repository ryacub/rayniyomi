package mihon.feature.upcoming.manga

import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.awaitAssert
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.service.LibraryPreferences
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingMangaScreenModelTest {

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
        val interactorCalls = env.stubInteractor(flowOf(listOf(manga(10))))

        val model = env.model()

        awaitAssert({ model.state.value.items }) { it.isNotEmpty() }

        assertEquals(2, model.state.value.items.size)
        assertEquals(emptyList<Long>() to emptyList<Long>(), interactorCalls.last())
    }

    @Test
    fun `cycleCategory cycles disabled to included to excluded to cleared`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        val interactorCalls = env.stubInteractor(flowOf(emptyList()))
        val model = env.model()
        awaitAssert({ interactorCalls.toList() }) { it.isNotEmpty() }
        val horror = category(id = 1, name = "Horror")

        model.cycleCategory(horror)
        assertEquals(setOf("1"), env.libraryPreferences.filterMangaUpcomingCategories().get())
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpcomingCategoriesExclude().get())

        model.cycleCategory(horror)
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpcomingCategories().get())
        awaitAssert({ interactorCalls.toList() }) { calls -> calls.any { it == emptyList<Long>() to listOf(1L) } }

        model.cycleCategory(horror)
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpcomingCategories().get())
        assertEquals(emptySet<String>(), env.libraryPreferences.filterMangaUpcomingCategoriesExclude().get())
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
        env.libraryPreferences.filterMangaUpcomingCategories().set(setOf("999"))
        val interactorCalls = env.stubInteractor(flowOf(listOf(manga(10))))

        val model = env.model()
        awaitAssert({ interactorCalls.toList() }) { calls -> calls.any { it.first == listOf(999L) } }
        awaitAssert({ model.state.value.items }) { it.isNotEmpty() }
    }

    @Test
    fun `manga cycleCategory does not mutate anime preferences`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        env.libraryPreferences.filterAnimeUpcomingCategories().set(setOf("5"))
        env.libraryPreferences.filterAnimeUpcomingCategoriesExclude().set(setOf("6"))
        val interactorCalls = env.stubInteractor(flowOf(emptyList()))
        val model = env.model()
        awaitAssert({ interactorCalls.toList() }) { it.isNotEmpty() }

        model.cycleCategory(category(id = 1, name = "Horror"))

        assertEquals(setOf("5"), env.libraryPreferences.filterAnimeUpcomingCategories().get())
        assertEquals(setOf("6"), env.libraryPreferences.filterAnimeUpcomingCategoriesExclude().get())
    }

    @Test
    fun `date grouping still emits items across dates`() = runTest(vt.scheduler) {
        val env = TestEnvironment()
        env.stubInteractor(
            flowOf(
                listOf(
                    manga(10, nextUpdate = 1_700_000_000_000L),
                    manga(20, nextUpdate = 1_700_259_200_000L),
                ),
            ),
        )

        val model = env.model()
        awaitAssert({ model.state.value.items }) { it.size == 4 }

        assertEquals(2, model.state.value.items.filterIsInstance<UpcomingMangaUIModel.Header>().size)
        assertEquals(2, model.state.value.items.filterIsInstance<UpcomingMangaUIModel.Item>().size)
    }

    private inner class TestEnvironment {
        val preferences = InMemoryPreferenceStore()
        val libraryPreferences = LibraryPreferences(preferences)
        val getUpcomingManga = mockk<GetUpcomingManga>()
        val getCategories = mockk<GetMangaCategories>()
        val interactorCalls = CopyOnWriteArrayList<Pair<List<Long>, List<Long>>>()

        init {
            every { getCategories.subscribe() } returns MutableStateFlow(
                listOf(category(id = 0, name = "")),
            )
        }

        fun stubInteractor(
            result: Flow<List<Manga>>,
        ): CopyOnWriteArrayList<Pair<List<Long>, List<Long>>> {
            coEvery { getUpcomingManga.subscribe(any(), any()) } answers {
                val included = firstArg<List<Long>>()
                val excluded = secondArg<List<Long>>()
                interactorCalls += included to excluded
                result
            }
            return interactorCalls
        }

        fun model(): UpcomingMangaScreenModel {
            return UpcomingMangaScreenModel(
                getUpcomingManga = getUpcomingManga,
                getCategories = getCategories,
                libraryPreferences = libraryPreferences,
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

    private fun manga(id: Long, nextUpdate: Long = 1_000L) = Manga.create().copy(
        id = id,
        title = "Manga $id",
        source = 1L,
        status = SManga.ONGOING.toLong(),
        nextUpdate = nextUpdate,
    )
}
