package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.awaitAssert
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.io.IOException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeSearchScreenModelTest {

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
    fun `newer search result wins when older request completes late`() = runTest(vt.scheduler) {
        val source = mockk<AnimeCatalogueSource>()
        val releaseOldRequest = CompletableDeferred<Unit>()

        every { source.id } returns 100L
        every { source.name } returns "Test Anime Source"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns true
        every { source.getFilterList() } returns AnimeFilterList()
        coEvery { source.getSearchAnime(1, any(), any()) } coAnswers {
            val query = secondArg<String>()
            when (query) {
                "old" -> {
                    // NonCancellable ensures the old request completes even after being
                    // superseded by a new search. This simulates a slow source that responds
                    // late, which is the race condition this coordinator prevents.
                    withContext(NonCancellable) { releaseOldRequest.await() }
                    AnimesPage(listOf(createSAnime("Old title", "/old")), false)
                }
                "new" -> AnimesPage(listOf(createSAnime("New title", "/new")), false)
                else -> error("Unexpected query: $query")
            }
        }

        val model = createModel(source)

        model.updateSearchQuery("old")
        model.search()
        advanceUntilIdle()

        model.updateSearchQuery("new")
        model.search()

        advanceUntilIdle()
        awaitAssert({ model.state.value.items[source] }) { result ->
            (result as? AnimeSearchItemResult.Success)?.result?.singleOrNull()?.title == "New title"
        }

        releaseOldRequest.complete(Unit)
        advanceUntilIdle()

        val finalResult = model.state.value.items[source]
        val success = assertInstanceOf(AnimeSearchItemResult.Success::class.java, finalResult)
        assertEquals("New title", success.result.single().title)
    }

    @Test
    fun `retrySource searches a failed source again`() = runTest(vt.scheduler) {
        val source = mockk<AnimeCatalogueSource>()
        var calls = 0

        every { source.id } returns 300L
        every { source.name } returns "Flaky source"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns true
        every { source.getFilterList() } returns AnimeFilterList()
        coEvery { source.getSearchAnime(1, "query", any()) } coAnswers {
            calls++
            if (calls == 1) throw IOException("blocked")
            AnimesPage(listOf(createSAnime("Found", "/found")), false)
        }

        val model = createModel(source)
        model.updateSearchQuery("query")
        model.search()
        advanceUntilIdle()
        assertInstanceOf(AnimeSearchItemResult.Error::class.java, model.state.value.items[source])

        model.retrySource(source)
        advanceUntilIdle()

        val success = assertInstanceOf(AnimeSearchItemResult.Success::class.java, model.state.value.items[source])
        assertEquals("Found", success.result.single().title)
        assertEquals(2, calls)
    }

    @Test
    fun `retrySource ignores a source that did not fail`() = runTest(vt.scheduler) {
        val source = mockk<AnimeCatalogueSource>()
        var calls = 0

        every { source.id } returns 301L
        every { source.name } returns "Working source"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns true
        every { source.getFilterList() } returns AnimeFilterList()
        coEvery { source.getSearchAnime(1, "query", any()) } coAnswers {
            calls++
            AnimesPage(listOf(createSAnime("Found", "/found")), false)
        }

        val model = createModel(source)
        model.updateSearchQuery("query")
        model.search()
        advanceUntilIdle()

        model.retrySource(source)
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `concurrent source completions retain every result`() = runTest(vt.scheduler) {
        Executors.newFixedThreadPool(5).asCoroutineDispatcher().use { dispatcher ->
            val releaseSources = CompletableDeferred<Unit>()
            val resultSorting = CyclicBarrier(5)
            val firstResultSort = ThreadLocal.withInitial { false }
            val sources = List(5) { index ->
                mockk<AnimeCatalogueSource> {
                    every { id } returns 400L + index
                    every { name } answers {
                        if (firstResultSort.get()) {
                            firstResultSort.set(false)
                            resultSorting.await(10, TimeUnit.SECONDS)
                        }
                        "Source $index"
                    }
                    every { lang } returns "en"
                    every { supportsLatest } returns true
                    every { getFilterList() } returns AnimeFilterList()
                    coEvery { getSearchAnime(1, "query", any()) } coAnswers {
                        releaseSources.await()
                        firstResultSort.set(true)
                        AnimesPage(emptyList(), false)
                    }
                }
            }
            val model = createModel(sources, dispatcher)
            try {
                val initialJobs = model.viewModelScope.coroutineContext[Job]!!.children.toSet()
                model.updateSearchQuery("query")
                model.search()
                val searchJob = model.viewModelScope.coroutineContext[Job]!!.children.single { it !in initialJobs }
                releaseSources.complete(Unit)
                withContext(Dispatchers.IO) {
                    withTimeout(15_000) { searchJob.join() }
                }

                assertEquals(sources.size, model.state.value.progress)
                sources.forEach { source ->
                    assertInstanceOf(AnimeSearchItemResult.Success::class.java, model.state.value.items[source])
                }
            } finally {
                model.viewModelScope.cancel()
            }
        }
    }

    private fun createModel(source: AnimeCatalogueSource): AnimeSearchScreenModel = createModel(listOf(source))

    private fun createModel(
        sources: List<AnimeCatalogueSource>,
        dispatcher: CoroutineDispatcher = vt.io,
    ): AnimeSearchScreenModel {
        val sourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = flowOf(sources)

            override fun get(sourceKey: Long): AnimeSource? = null

            override fun getOrStub(sourceKey: Long): AnimeSource {
                error("Not used in this test")
            }

            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()

            override fun getCatalogueSources(): List<AnimeCatalogueSource> = sources

            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val networkToLocalAnime = mockk<NetworkToLocalAnime>()
        coEvery { networkToLocalAnime.await(any()) } coAnswers {
            firstArg<Anime>().copy(id = 1L)
        }

        val sourcePreferences = testSourcePreferences()
        return object : AnimeSearchScreenModel(
            sourcePreferences = sourcePreferences,
            sourceManager = sourceManager,
            extensionManager = mockk<AnimeExtensionManager>(relaxed = true),
            networkToLocalAnime = networkToLocalAnime,
            getAnime = mockk<GetAnime>(relaxed = true),
            preferences = sourcePreferences,
            searchDispatcher = dispatcher,
        ) {}
    }

    private fun testSourcePreferences(): SourcePreferences {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "source_languages",
                    data = setOf("en"),
                    defaultValue = emptySet<String>(),
                ),
            ),
        )
        return SourcePreferences(store)
    }

    private fun createSAnime(title: String, url: String): SAnime {
        return SAnime.create().apply {
            this.title = title
            this.url = url
        }
    }
}
