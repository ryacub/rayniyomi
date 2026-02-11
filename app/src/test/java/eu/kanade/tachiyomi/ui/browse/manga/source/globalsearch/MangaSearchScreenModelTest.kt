package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
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
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class MangaSearchScreenModelTest {

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
        val source = mockk<CatalogueSource>()
        val oldRequestStarted = CompletableDeferred<Unit>()
        val releaseOldRequest = CompletableDeferred<Unit>()

        every { source.id } returns 200L
        every { source.name } returns "Test Manga Source"
        every { source.lang } returns "en"
        every { source.supportsLatest } returns true
        every { source.getFilterList() } returns FilterList()
        coEvery { source.getSearchManga(1, any(), any()) } coAnswers {
            val query = secondArg<String>()
            when (query) {
                "old" -> {
                    oldRequestStarted.complete(Unit)
                    // NonCancellable ensures the old request completes even after being
                    // superseded by a new search. This simulates a slow source that responds
                    // late, which is the race condition this coordinator prevents.
                    withContext(NonCancellable) { releaseOldRequest.await() }
                    MangasPage(listOf(createSManga("Old title", "/old")), false)
                }
                "new" -> MangasPage(listOf(createSManga("New title", "/new")), false)
                else -> error("Unexpected query: $query")
            }
        }

        val sourceManager = mockk<MangaSourceManager>()
        every { sourceManager.getCatalogueSources() } returns listOf(source)

        val networkToLocalManga = mockk<NetworkToLocalManga>()
        coEvery { networkToLocalManga.await(any()) } coAnswers {
            firstArg<Manga>().copy(id = 1L)
        }

        val sourcePreferences = testSourcePreferences()
        val model = object : MangaSearchScreenModel(
            sourcePreferences = sourcePreferences,
            sourceManager = sourceManager,
            extensionManager = mockk<MangaExtensionManager>(relaxed = true),
            networkToLocalManga = networkToLocalManga,
            getManga = mockk<GetManga>(relaxed = true),
            preferences = sourcePreferences,
        ) {}

        model.updateSearchQuery("old")
        model.search()
        oldRequestStarted.await()

        model.updateSearchQuery("new")
        model.search()

        eventually {
            val result = model.state.value.items[source] as? MangaSearchItemResult.Success
            result?.result?.singleOrNull()?.title == "New title"
        }

        releaseOldRequest.complete(Unit)
        delay(150)

        val finalResult = model.state.value.items[source]
        val success = assertInstanceOf(MangaSearchItemResult.Success::class.java, finalResult)
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

    private fun createSManga(title: String, url: String): SManga {
        return SManga.create().apply {
            this.title = title
            this.url = url
        }
    }
}
