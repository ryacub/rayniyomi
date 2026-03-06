package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.interactor.ComputeCompositeScore
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

class EntryEnrichmentCoordinatorTest {

    @Test
    fun `refreshManga returns empty recommendations when trackers are not logged in`() = runBlocking {
        val trackerManager = mockk<TrackerManager>()
        val tracker = mockk<BaseTracker>(relaxed = true)
        every { tracker.isLoggedIn } returns false
        every { trackerManager.get(any()) } returns tracker

        val getMangaTracks = mockk<GetMangaTracks>()
        coEvery { getMangaTracks.await(any()) } returns listOf(
            MangaTrack(
                id = 1L,
                mangaId = 10L,
                trackerId = 2L,
                remoteId = 99L,
                libraryId = null,
                title = "Tracked Title",
                lastChapterRead = 0.0,
                totalChapters = 0L,
                status = 0L,
                score = 0.0,
                remoteUrl = "",
                startDate = 0L,
                finishDate = 0L,
                private = false,
            ),
        )

        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getManga(any()) } returns null
        coJustRun { cacheRepository.upsertManga(any(), any()) }

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = getMangaTracks,
            getAnimeTracks = mockk<GetAnimeTracks>(relaxed = true),
            getLibraryManga = mockk<GetLibraryManga> {
                coEvery { await() } returns emptyList()
            },
            getLibraryAnime = mockk<GetLibraryAnime>(relaxed = true),
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val enriched = coordinator.refreshManga(mangaId = 10L, title = "Tracked Title", force = true)

        assertTrue(enriched.recommendations.isEmpty())
    }

    @Test
    fun `generic refreshEntry returns cached entry when cache is valid and force is false`() = runBlocking {
        val now = System.currentTimeMillis()
        val futureExpiresAt = now + 1000 * 60 * 60 // 1 hour in future
        val cachedEntry = mockk<EnrichedEntry>()
        every { cachedEntry.expiresAt } returns futureExpiresAt

        val trackerManager = mockk<TrackerManager>()
        val getMangaTracks = mockk<GetMangaTracks>()
        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getManga(10L) } returns cachedEntry

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = getMangaTracks,
            getAnimeTracks = mockk<GetAnimeTracks>(relaxed = true),
            getLibraryManga = mockk<GetLibraryManga>(relaxed = true),
            getLibraryAnime = mockk<GetLibraryAnime>(relaxed = true),
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val result = coordinator.refreshManga(mangaId = 10L, title = "Test Title", force = false)

        assertEquals(result, cachedEntry)
    }

    @Test
    fun `generic refreshEntry processes empty tracks list and returns no candidates`() = runBlocking {
        val trackerManager = mockk<TrackerManager>()
        val getMangaTracks = mockk<GetMangaTracks>()
        coEvery { getMangaTracks.await(10L) } returns emptyList()

        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getManga(10L) } returns null
        coJustRun { cacheRepository.upsertManga(any(), any()) }

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = getMangaTracks,
            getAnimeTracks = mockk<GetAnimeTracks>(relaxed = true),
            getLibraryManga = mockk<GetLibraryManga> {
                coEvery { await() } returns emptyList()
            },
            getLibraryAnime = mockk<GetLibraryAnime>(relaxed = true),
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val result = coordinator.refreshManga(mangaId = 10L, title = "Test Title", force = true)

        assertNotNull(result)
        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `generic refreshEntry skips null tracker returned by trackerManager`() = runBlocking {
        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.get(any()) } returns null

        val getMangaTracks = mockk<GetMangaTracks>()
        coEvery { getMangaTracks.await(10L) } returns listOf(
            MangaTrack(
                id = 1L,
                mangaId = 10L,
                trackerId = 2L,
                remoteId = 99L,
                libraryId = null,
                title = "Tracked Title",
                lastChapterRead = 0.0,
                totalChapters = 0L,
                status = 0L,
                score = 0.0,
                remoteUrl = "",
                startDate = 0L,
                finishDate = 0L,
                private = false,
            ),
        )

        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getManga(10L) } returns null
        coJustRun { cacheRepository.upsertManga(any(), any()) }

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = getMangaTracks,
            getAnimeTracks = mockk<GetAnimeTracks>(relaxed = true),
            getLibraryManga = mockk<GetLibraryManga> {
                coEvery { await() } returns emptyList()
            },
            getLibraryAnime = mockk<GetLibraryAnime>(relaxed = true),
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val result = coordinator.refreshManga(mangaId = 10L, title = "Test Title", force = true)

        assertNotNull(result)
        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `generic refreshEntry skips tracker when isLoggedIn is false`() = runBlocking {
        val trackerManager = mockk<TrackerManager>()
        val tracker = mockk<BaseTracker>(relaxed = true)
        every { tracker.isLoggedIn } returns false
        every { trackerManager.get(any()) } returns tracker

        val getMangaTracks = mockk<GetMangaTracks>()
        coEvery { getMangaTracks.await(10L) } returns listOf(
            MangaTrack(
                id = 1L,
                mangaId = 10L,
                trackerId = 2L,
                remoteId = 99L,
                libraryId = null,
                title = "Tracked Title",
                lastChapterRead = 0.0,
                totalChapters = 0L,
                status = 0L,
                score = 0.0,
                remoteUrl = "",
                startDate = 0L,
                finishDate = 0L,
                private = false,
            ),
        )

        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getManga(10L) } returns null
        coJustRun { cacheRepository.upsertManga(any(), any()) }

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = getMangaTracks,
            getAnimeTracks = mockk<GetAnimeTracks>(relaxed = true),
            getLibraryManga = mockk<GetLibraryManga> {
                coEvery { await() } returns emptyList()
            },
            getLibraryAnime = mockk<GetLibraryAnime>(relaxed = true),
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val result = coordinator.refreshManga(mangaId = 10L, title = "Test Title", force = true)

        assertNotNull(result)
        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun `generic refreshEntry returns EnrichedEntry with correct mediaType MANGA`() = runBlocking {
        val trackerManager = mockk<TrackerManager>()
        val getMangaTracks = mockk<GetMangaTracks>()
        coEvery { getMangaTracks.await(10L) } returns emptyList()

        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getManga(10L) } returns null
        coJustRun { cacheRepository.upsertManga(any(), any()) }

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = getMangaTracks,
            getAnimeTracks = mockk<GetAnimeTracks>(relaxed = true),
            getLibraryManga = mockk<GetLibraryManga> {
                coEvery { await() } returns emptyList()
            },
            getLibraryAnime = mockk<GetLibraryAnime>(relaxed = true),
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val result = coordinator.refreshManga(mangaId = 10L, title = "Test Title", force = true)

        assertEquals(result.mediaType, EnrichmentMediaType.MANGA)
        assertEquals(result.entryId, 10L)
    }

    @Test
    fun `generic refreshEntry returns EnrichedEntry with correct mediaType ANIME`() = runBlocking {
        val trackerManager = mockk<TrackerManager>()
        val getAnimeTracks = mockk<GetAnimeTracks>()
        coEvery { getAnimeTracks.await(20L) } returns emptyList()

        val cacheRepository = mockk<EnrichmentCacheRepository>()
        coEvery { cacheRepository.getAnime(20L) } returns null
        coJustRun { cacheRepository.upsertAnime(any(), any()) }

        val coordinator = EntryEnrichmentCoordinator(
            trackerManager = trackerManager,
            getMangaTracks = mockk<GetMangaTracks>(relaxed = true),
            getAnimeTracks = getAnimeTracks,
            getLibraryManga = mockk<GetLibraryManga>(relaxed = true),
            getLibraryAnime = mockk<GetLibraryAnime> {
                coEvery { await() } returns emptyList()
            },
            cacheRepository = cacheRepository,
            recommendationAggregator = RecommendationAggregator(),
            computeCompositeScore = ComputeCompositeScore(),
        )

        val result = coordinator.refreshAnime(animeId = 20L, title = "Test Title", force = true)

        assertEquals(result.mediaType, EnrichmentMediaType.ANIME)
        assertEquals(result.entryId, 20L)
    }
}
