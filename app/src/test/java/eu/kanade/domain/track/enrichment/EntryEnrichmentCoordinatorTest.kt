package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.interactor.ComputeCompositeScore
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
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
}
