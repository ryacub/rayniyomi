package tachiyomi.data.source.manga

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager

class MangaSourceRepositoryImplTest {

    @Test
    fun `getMangaSources upserts known runtime metadata`() = runBlocking {
        val source = mockCatalogueSource(id = 1L, lang = "en", name = "Runtime Manga", supportsLatest = true)
        val sourceManager = mockk<MangaSourceManager> {
            every { catalogueSources } returns flowOf(listOf(source))
        }
        val stubRepo = mockk<MangaStubSourceRepository>(relaxed = true)

        val repository = MangaSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = mockk<MangaDatabaseHandler>(),
            stubSourceRepository = stubRepo,
        )

        val result = repository.getMangaSources().first()

        result.shouldHaveSize(1)
        result.first().name shouldBe "Runtime Manga"
        result.first().lang shouldBe "en"
        result.first().supportsLatest shouldBe true
        coVerify(exactly = 1) { stubRepo.upsertStubMangaSource(1L, "en", "Runtime Manga") }
    }

    @Test
    fun `getOnlineMangaSources filters to http sources and still upserts catalogue metadata`() = runBlocking {
        val catalogueSource =
            mockCatalogueSource(id = 10L, lang = "ja", name = "Catalogue Only", supportsLatest = false)
        val httpSource = mockHttpSource(id = 11L, lang = "en", name = "Http Source")
        val sourceManager = mockk<MangaSourceManager> {
            every { catalogueSources } returns flowOf(listOf(catalogueSource, httpSource))
        }
        val stubRepo = mockk<MangaStubSourceRepository>(relaxed = true)

        val repository = MangaSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = mockk<MangaDatabaseHandler>(),
            stubSourceRepository = stubRepo,
        )

        val result = repository.getOnlineMangaSources().first()

        result.shouldHaveSize(1)
        result.first().id shouldBe 11L
        coVerify(exactly = 1) { stubRepo.upsertStubMangaSource(10L, "ja", "Catalogue Only") }
        coVerify(exactly = 1) { stubRepo.upsertStubMangaSource(11L, "en", "Http Source") }
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
}
