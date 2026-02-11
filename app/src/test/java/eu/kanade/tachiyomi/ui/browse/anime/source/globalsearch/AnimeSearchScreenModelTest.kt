package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeSearchScreenModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `newer search result wins when older request completes late`() = runBlocking {
        val source = mockk<AnimeCatalogueSource>()
        val oldRequestStarted = CompletableDeferred<Unit>()
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
                    oldRequestStarted.complete(Unit)
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

        val sourceManager = object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = flowOf(listOf(source))

            override fun get(sourceKey: Long): AnimeSource? = null

            override fun getOrStub(sourceKey: Long): AnimeSource {
                error("Not used in this test")
            }

            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()

            override fun getCatalogueSources(): List<AnimeCatalogueSource> = listOf(source)

            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }

        val networkToLocalAnime = mockk<NetworkToLocalAnime>()
        coEvery { networkToLocalAnime.await(any()) } coAnswers {
            firstArg<Anime>().copy(id = 1L)
        }

        val sourcePreferences = testSourcePreferences()
        val model = object : AnimeSearchScreenModel(
            sourcePreferences = sourcePreferences,
            sourceManager = sourceManager,
            extensionManager = mockk<AnimeExtensionManager>(relaxed = true),
            networkToLocalAnime = networkToLocalAnime,
            getAnime = mockk<GetAnime>(relaxed = true),
            preferences = sourcePreferences,
        ) {}

        model.updateSearchQuery("old")
        model.search()
        oldRequestStarted.await()

        model.updateSearchQuery("new")
        model.search()

        eventually {
            val result = model.state.value.items[source] as? AnimeSearchItemResult.Success
            result?.result?.singleOrNull()?.title == "New title"
        }

        releaseOldRequest.complete(Unit)
        delay(150)

        val finalResult = model.state.value.items[source]
        val success = assertInstanceOf(AnimeSearchItemResult.Success::class.java, finalResult)
        assertEquals("New title", success.result.single().title)
    }

    private suspend fun eventually(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
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
