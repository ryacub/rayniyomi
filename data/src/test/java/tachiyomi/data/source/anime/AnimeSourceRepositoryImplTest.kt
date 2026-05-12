package tachiyomi.data.source.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class AnimeSourceRepositoryImplTest {

    @Test
    fun `getAnimeSources upserts known runtime metadata`() = runBlocking {
        val source = mockAnimeCatalogueSource(id = 1L, lang = "en", name = "Runtime Anime", supportsLatest = true)
        val sourceManager = mockk<AnimeSourceManager> {
            every { catalogueSources } returns flowOf(listOf(source))
        }
        val stubRepo = mockk<AnimeStubSourceRepository>(relaxed = true)

        val repository = AnimeSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = mockk<AnimeDatabaseHandler>(),
            stubSourceRepository = stubRepo,
        )

        val result = repository.getAnimeSources().first()

        result.shouldHaveSize(1)
        result.first().name shouldBe "Runtime Anime"
        result.first().lang shouldBe "en"
        result.first().supportsLatest shouldBe true
        coVerify(exactly = 1) { stubRepo.upsertStubAnimeSource(1L, "en", "Runtime Anime") }
    }

    @Test
    fun `getOnlineAnimeSources filters to http sources and still upserts catalogue metadata`() = runBlocking {
        val catalogueSource =
            mockAnimeCatalogueSource(id = 10L, lang = "ja", name = "Catalogue Only", supportsLatest = false)
        val httpSource = mockAnimeHttpSource(id = 11L, lang = "en", name = "Http Anime")
        val sourceManager = mockk<AnimeSourceManager> {
            every { catalogueSources } returns flowOf(listOf(catalogueSource, httpSource))
        }
        val stubRepo = mockk<AnimeStubSourceRepository>(relaxed = true)

        val repository = AnimeSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = mockk<AnimeDatabaseHandler>(),
            stubSourceRepository = stubRepo,
        )

        val result = repository.getOnlineAnimeSources().first()

        result.shouldHaveSize(1)
        result.first().id shouldBe 11L
        coVerify(exactly = 1) { stubRepo.upsertStubAnimeSource(10L, "ja", "Catalogue Only") }
        coVerify(exactly = 1) { stubRepo.upsertStubAnimeSource(11L, "en", "Http Anime") }
    }

    private fun mockAnimeCatalogueSource(
        id: Long,
        lang: String,
        name: String,
        supportsLatest: Boolean,
    ): AnimeCatalogueSource {
        return mockk<AnimeCatalogueSource>(relaxed = true).apply {
            every { this@apply.id } returns id
            every { this@apply.lang } returns lang
            every { this@apply.name } returns name
            every { this@apply.supportsLatest } returns supportsLatest
        }
    }

    private fun mockAnimeHttpSource(
        id: Long,
        lang: String,
        name: String,
    ): AnimeHttpSource {
        return mockk<AnimeHttpSource>(relaxed = true).apply {
            every { this@apply.id } returns id
            every { this@apply.lang } returns lang
            every { this@apply.name } returns name
        }
    }
}
