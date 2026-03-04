package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.DiscoverCacheSnapshot
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga

class DiscoverFeedCoordinatorTest {

    @Test
    fun `refresh returns empty list when recommendations are unavailable`() = runBlocking {
        val repository = FakeRepository(
            recommendations = emptyList(),
            snapshots = emptyList(),
        )
        val coordinator = DiscoverFeedCoordinator(
            cacheRepository = repository,
            rankingEngine = DiscoverRankingEngine(),
            bulkEnrichmentCoordinator = mockk(relaxed = true),
            getLibraryManga = mockEmptyMangaLibrary(),
            getLibraryAnime = mockEmptyAnimeLibrary(),
        )

        val result = coordinator.refresh(limit = 20, force = false)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `refresh keeps media types separated for same stable key`() = runBlocking {
        val recommendation = AggregatedRecommendation(
            stableKey = "shared-key",
            title = "Common Title",
            targetUrl = "https://example.org/title",
            trackerSources = listOf("AniList"),
            sourceCount = 1,
            confidence = 0.85,
            inLibrary = false,
            rankScore = 1.0,
        )
        val repository = FakeRepository(
            recommendations = listOf(
                DiscoverRecommendationRecord(
                    entryId = 1L,
                    mediaType = EnrichmentMediaType.MANGA,
                    recommendation = recommendation,
                    updatedAt = 100L,
                ),
                DiscoverRecommendationRecord(
                    entryId = 2L,
                    mediaType = EnrichmentMediaType.ANIME,
                    recommendation = recommendation,
                    updatedAt = 110L,
                ),
            ),
            snapshots = listOf(
                DiscoverCacheSnapshot(1L, EnrichmentMediaType.MANGA, 100L, 200L, 7.0),
                DiscoverCacheSnapshot(2L, EnrichmentMediaType.ANIME, 110L, 210L, 8.0),
            ),
        )
        val coordinator = DiscoverFeedCoordinator(
            cacheRepository = repository,
            rankingEngine = DiscoverRankingEngine(),
            bulkEnrichmentCoordinator = mockk(relaxed = true),
            getLibraryManga = mockEmptyMangaLibrary(),
            getLibraryAnime = mockEmptyAnimeLibrary(),
        )

        val result = coordinator.refresh(limit = 20, force = false)

        assertEquals(2, result.size)
        assertTrue(result.any { it.mediaType == EnrichmentMediaType.MANGA })
        assertTrue(result.any { it.mediaType == EnrichmentMediaType.ANIME })
    }

    private fun mockEmptyMangaLibrary(): GetLibraryManga {
        return mockk {
            coEvery { await() } returns emptyList()
        }
    }

    private fun mockEmptyAnimeLibrary(): GetLibraryAnime {
        return mockk {
            coEvery { await() } returns emptyList()
        }
    }

    private class FakeRepository(
        private val recommendations: List<DiscoverRecommendationRecord>,
        private val snapshots: List<DiscoverCacheSnapshot>,
    ) : EnrichmentCacheRepository {
        override suspend fun getManga(entryId: Long) = null

        override suspend fun getAnime(entryId: Long) = null

        override fun observeManga(entryId: Long) = emptyFlow<Nothing?>()

        override fun observeAnime(entryId: Long) = emptyFlow<Nothing?>()

        override suspend fun upsertManga(entryId: Long, entry: eu.kanade.domain.track.enrichment.model.EnrichedEntry) {
            Unit
        }

        override suspend fun upsertAnime(entryId: Long, entry: eu.kanade.domain.track.enrichment.model.EnrichedEntry) {
            Unit
        }

        override suspend fun getDiscoverRecommendations(limit: Long): List<DiscoverRecommendationRecord> {
            return recommendations
        }

        override suspend fun getDiscoverSnapshots(): List<DiscoverCacheSnapshot> {
            return snapshots
        }

        override fun observeDiscoverRecommendations(limit: Long) = emptyFlow<List<DiscoverRecommendationRecord>>()

        override fun observeDiscoverSnapshots() = emptyFlow<List<DiscoverCacheSnapshot>>()
    }
}
