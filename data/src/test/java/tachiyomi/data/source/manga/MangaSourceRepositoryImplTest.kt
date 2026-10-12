package tachiyomi.data.source.manga

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager

class MangaSourceRepositoryImplTest {

    @Test
    fun `getMangaSources maps runtime metadata`() = runBlocking<Unit> {
        val source = mockCatalogueSource(id = 1L, lang = "en", name = "Runtime Manga", supportsLatest = true)
        val sourceManager = TestMangaSourceManager(listOf(source))

        val repository = MangaSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = mockk<MangaDatabaseHandler>(),
        )

        val result = repository.getMangaSources().first()

        result.shouldHaveSize(1)
        result.first().name shouldBe "Runtime Manga"
        result.first().lang shouldBe "en"
        result.first().supportsLatest shouldBe true
    }

    @Test
    fun `getOnlineMangaSources filters to http sources`() = runBlocking<Unit> {
        val catalogueSource =
            mockCatalogueSource(id = 10L, lang = "ja", name = "Catalogue Only", supportsLatest = false)
        val httpSource = mockHttpSource(id = 11L, lang = "en", name = "Http Source")
        val sourceManager = TestMangaSourceManager(listOf(catalogueSource, httpSource))

        val repository = MangaSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = mockk<MangaDatabaseHandler>(),
        )

        val result = repository.getOnlineMangaSources().first()

        result.shouldHaveSize(1)
        result.first().id shouldBe 11L
    }

    private fun mockCatalogueSource(
        id: Long,
        lang: String,
        name: String,
        supportsLatest: Boolean,
    ): CatalogueSource {
        return mockk<CatalogueSource>(relaxed = true).apply {
            every { this@apply.id } returns id
            every { this@apply.lang } returns lang
            every { this@apply.name } returns name
            every { this@apply.supportsLatest } returns supportsLatest
        }
    }

    private fun mockHttpSource(
        id: Long,
        lang: String,
        name: String,
    ): HttpSource {
        return mockk<HttpSource>(relaxed = true).apply {
            every { this@apply.id } returns id
            every { this@apply.lang } returns lang
            every { this@apply.name } returns name
        }
    }

    private class TestMangaSourceManager(
        private val mangaSources: List<MangaSource>,
    ) : MangaSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val sources: Flow<List<MangaSource>> = flowOf(mangaSources)

        override fun get(sourceKey: Long) = mangaSources.firstOrNull { it.id == sourceKey }

        override fun getOrStub(sourceKey: Long) = get(sourceKey) ?: error("Unknown source id: $sourceKey")

        override fun getAll(): List<MangaSource> = mangaSources

        override fun getOnlineSources(): List<HttpSource> = mangaSources.filterIsInstance<HttpSource>()

        override fun getStubSources(): List<StubMangaSource> = emptyList()
    }
}
