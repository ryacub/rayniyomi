package eu.kanade.domain.track.enrichment.evolve

import eu.kanade.domain.track.enrichment.BulkEnrichmentCoordinator
import eu.kanade.domain.track.enrichment.DiscoverFeedCoordinator
import eu.kanade.domain.track.enrichment.DiscoverRankingEngine
import eu.kanade.domain.track.enrichment.DiscoverRecommendationRecord
import eu.kanade.domain.track.enrichment.EnrichmentCacheRepository
import eu.kanade.domain.track.enrichment.interactor.RefreshAnimeEnrichment
import eu.kanade.domain.track.enrichment.interactor.RefreshMangaEnrichment
import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.DiscoverCacheSnapshot
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga

/**
 * Evolve Experiment A: DiscoverFeedCoordinator.buildFeed throughput benchmark.
 *
 * Dataset: 400 synthetic recommendations across 50 seed entries (25 manga + 25 anime),
 * 200 library items (100 manga + 100 anime), 50 cache snapshots.
 *
 * Run with:
 *   ./gradlew :app:testDebugUnitTest --tests "*FeedBench*"
 *
 * Parses output line: "METRIC: <avg_ms_per_call>"
 * Lower is better.
 */
class FeedBench {

    @Test
    fun `benchmark buildFeed throughput`() = runBlocking {
        val coordinator = buildCoordinator()

        // Warmup: prime JIT
        repeat(10) { coordinator.refresh(limit = 50, force = false) }

        // Measure: 100 iterations of full refresh → buildFeed pipeline
        val iterations = 100
        val start = System.nanoTime()
        repeat(iterations) { coordinator.refresh(limit = 50, force = false) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        val avgMs = elapsedMs / iterations
        println("METRIC: ${"%.3f".format(avgMs)}")
    }

    private fun buildCoordinator(): DiscoverFeedCoordinator {
        val genres = listOf(
            "action", "fantasy", "romance", "comedy", "drama",
            "sci-fi", "horror", "mystery", "slice of life", "sports",
        )
        val now = System.currentTimeMillis()

        // 100 manga library items — genres cycle through the pool
        val mangaLibrary = (1L..100L).map { id ->
            val i = id.toInt()
            LibraryManga(
                manga = mockk {
                    every { this@mockk.id } returns id
                    every { title } returns "Manga $id"
                    every { genre } returns listOf(genres[i % 10], genres[(i + 1) % 10], genres[(i + 2) % 10])
                },
                category = 0L,
                totalChapters = 50L,
                readCount = 25L,
                bookmarkCount = 0L,
                latestUpload = 0L,
                chapterFetchedAt = 0L,
                lastRead = 0L,
            )
        }

        // 100 anime library items
        val animeLibrary = (101L..200L).map { id ->
            val i = id.toInt()
            LibraryAnime(
                anime = mockk {
                    every { this@mockk.id } returns id
                    every { title } returns "Anime $id"
                    every { genre } returns listOf(genres[i % 10], genres[(i + 3) % 10], genres[(i + 5) % 10])
                },
                category = 0L,
                totalCount = 12L,
                seenCount = 6L,
                bookmarkCount = 0L,
                fillermarkCount = 0L,
                latestUpload = 0L,
                episodeFetchedAt = 0L,
                lastSeen = 0L,
            )
        }

        // 50 cache snapshots: seeds 1-25 manga + 101-125 anime
        val snapshots: List<DiscoverCacheSnapshot> =
            (1L..25L).map { id ->
                DiscoverCacheSnapshot(
                    entryId = id,
                    mediaType = EnrichmentMediaType.MANGA,
                    updatedAt = now - 1_000L,
                    expiresAt = now + 86_400_000L,
                    compositeScore = 5.0 + (id % 5),
                )
            } + (101L..125L).map { id ->
                DiscoverCacheSnapshot(
                    entryId = id,
                    mediaType = EnrichmentMediaType.ANIME,
                    updatedAt = now - 1_000L,
                    expiresAt = now + 86_400_000L,
                    compositeScore = 6.0 + (id % 4),
                )
            }

        // 400 recommendations: 8 recs per seed × 50 seeds
        // stableKey cycles through 200 unique keys → ~50% dedup ratio, exercising groupBy
        var idx = 0
        val recommendations: List<DiscoverRecommendationRecord> = buildList {
            (1L..25L).forEach { seedId ->
                repeat(8) {
                    val key = idx % 200
                    add(
                        DiscoverRecommendationRecord(
                            entryId = seedId,
                            mediaType = EnrichmentMediaType.MANGA,
                            updatedAt = now,
                            recommendation = AggregatedRecommendation(
                                stableKey = "manga-rec-$key",
                                title = "Manga Rec $key",
                                targetUrl = "https://example.com/manga/$key",
                                trackerSources = if (idx % 3 == 0) listOf("AniList", "MyAnimeList") else listOf("AniList"),
                                sourceCount = if (idx % 3 == 0) 2 else 1,
                                confidence = 0.5 + (idx % 5) * 0.1,
                                inLibrary = idx % 5 == 0, // 20% filtered by buildFeed
                                rankScore = 5.0 + (idx % 10) * 0.3,
                            ),
                        ),
                    )
                    idx++
                }
            }
            (101L..125L).forEach { seedId ->
                repeat(8) {
                    val key = idx % 200
                    add(
                        DiscoverRecommendationRecord(
                            entryId = seedId,
                            mediaType = EnrichmentMediaType.ANIME,
                            updatedAt = now,
                            recommendation = AggregatedRecommendation(
                                stableKey = "anime-rec-$key",
                                title = "Anime Rec $key",
                                targetUrl = "https://example.com/anime/$key",
                                trackerSources = if (idx % 3 == 0) listOf("AniList", "Kitsu") else listOf("Kitsu"),
                                sourceCount = if (idx % 3 == 0) 2 else 1,
                                confidence = 0.5 + (idx % 5) * 0.1,
                                inLibrary = idx % 5 == 0,
                                rankScore = 5.0 + (idx % 10) * 0.3,
                            ),
                        ),
                    )
                    idx++
                }
            }
        }

        val cacheRepository = mockk<EnrichmentCacheRepository> {
            coEvery { getDiscoverRecommendations(any()) } returns recommendations
            coEvery { getDiscoverSnapshots() } returns snapshots
            every { observeDiscoverRecommendations(any()) } returns flowOf(recommendations)
            every { observeDiscoverSnapshots() } returns flowOf(snapshots)
            coEvery { getManga(any()) } returns null
            coEvery { getAnime(any()) } returns null
            every { observeManga(any()) } returns flowOf(null)
            every { observeAnime(any()) } returns flowOf(null)
            coEvery { upsertManga(any(), any()) } returns Unit
            coEvery { upsertAnime(any(), any()) } returns Unit
        }

        // BulkEnrichmentCoordinator with empty libraries: refreshAll() launches 0 jobs (instant no-op)
        val emptyGetManga = mockk<GetLibraryManga> { coEvery { await() } returns emptyList() }
        val emptyGetAnime = mockk<GetLibraryAnime> { coEvery { await() } returns emptyList() }
        val bulkCoordinator = BulkEnrichmentCoordinator(
            getLibraryManga = emptyGetManga,
            getLibraryAnime = emptyGetAnime,
            refreshMangaEnrichment = mockk<RefreshMangaEnrichment>(),
            refreshAnimeEnrichment = mockk<RefreshAnimeEnrichment>(),
        )

        val getLibraryManga = mockk<GetLibraryManga> { coEvery { await() } returns mangaLibrary }
        val getLibraryAnime = mockk<GetLibraryAnime> { coEvery { await() } returns animeLibrary }

        return DiscoverFeedCoordinator(
            cacheRepository = cacheRepository,
            rankingEngine = DiscoverRankingEngine(),
            bulkEnrichmentCoordinator = bulkCoordinator,
            getLibraryManga = getLibraryManga,
            getLibraryAnime = getLibraryAnime,
        )
    }
}
