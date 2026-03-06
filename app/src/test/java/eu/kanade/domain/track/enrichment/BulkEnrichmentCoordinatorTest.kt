package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.interactor.RefreshAnimeEnrichment
import eu.kanade.domain.track.enrichment.interactor.RefreshMangaEnrichment
import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import java.util.concurrent.atomic.AtomicInteger

class BulkEnrichmentCoordinatorTest {

    @Test
    fun `refreshAll throws exception from anime before slow manga job completes when using awaitAll`() = runBlocking {
        // Discriminating test: awaitAll() throws the FIRST chronological exception.
        // Sequential forEach { it.await() } forces manga to fully complete before awaiting anime,
        // so manga increments the completion counter BEFORE the anime failure is observed.
        // With awaitAll(), the fast-failing anime job cancels the slow manga job before it completes.
        val mangaCompletionCount = AtomicInteger(0)
        val animeException = RuntimeException("anime failed early")

        val getLibraryManga = mockk<GetLibraryManga> {
            coEvery { await() } returns listOf(createMockLibraryManga(id = 1L))
        }

        val getLibraryAnime = mockk<GetLibraryAnime> {
            coEvery { await() } returns listOf(createMockLibraryAnime(id = 1L))
        }

        val refreshMangaEnrichment = mockk<RefreshMangaEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                delay(500) // slow manga job — takes 500ms to "complete"
                mangaCompletionCount.incrementAndGet() // only reached if NOT cancelled
                createMockEnrichedEntry(id = 1L)
            }
        }

        val refreshAnimeEnrichment = mockk<RefreshAnimeEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                // fails immediately — no delay
                throw animeException
            }
        }

        val coordinator = BulkEnrichmentCoordinator(
            getLibraryManga = getLibraryManga,
            getLibraryAnime = getLibraryAnime,
            refreshMangaEnrichment = refreshMangaEnrichment,
            refreshAnimeEnrichment = refreshAnimeEnrichment,
        )

        assertThrows(RuntimeException::class.java) {
            runBlocking { coordinator.refreshAll(force = false) }
        }

        // With awaitAll(): anime fails immediately → exception thrown → manga cancelled → count stays 0
        // With sequential forEach { it.await() }: manga awaited first (500ms), completes → count = 1,
        //   THEN anime failure is observed → count is already 1.
        assertEquals(
            0,
            mangaCompletionCount.get(),
            "Slow manga job should be cancelled when fast-failing anime exception is thrown. " +
                "With sequential forEach { it.await() }, manga completes first (count=1). " +
                "With awaitAll(), manga is cancelled before completing (count=0).",
        )
    }

    @Test
    fun `refreshAll with exception in one job propagates first exception and lets others complete`() = runBlocking {
        val jobCompletionTracker = mutableSetOf<String>()
        val completionException = RuntimeException("Manga enrichment failed")

        val getLibraryManga = mockk<GetLibraryManga> {
            coEvery { await() } returns listOf(
                createMockLibraryManga(id = 1L),
                createMockLibraryManga(id = 2L),
            )
        }

        val getLibraryAnime = mockk<GetLibraryAnime> {
            coEvery { await() } returns listOf(
                createMockLibraryAnime(id = 1L),
            )
        }

        var failureCount = 0
        val refreshMangaEnrichment = mockk<RefreshMangaEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                if (failureCount++ == 0) {
                    throw completionException
                }
                jobCompletionTracker.add("manga")
                delay(10)
                createMockEnrichedEntry(id = 1L)
            }
        }

        val refreshAnimeEnrichment = mockk<RefreshAnimeEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                jobCompletionTracker.add("anime")
                delay(10)
                createMockEnrichedEntry(id = 1L)
            }
        }

        val coordinator = BulkEnrichmentCoordinator(
            getLibraryManga = getLibraryManga,
            getLibraryAnime = getLibraryAnime,
            refreshMangaEnrichment = refreshMangaEnrichment,
            refreshAnimeEnrichment = refreshAnimeEnrichment,
        )

        // awaitAll() should throw the first exception
        val exception = assertThrows(RuntimeException::class.java) {
            runBlocking { coordinator.refreshAll(force = false) }
        }

        assertEquals(completionException.message, exception.message)
    }

    @Test
    fun `refreshAll with empty manga list executes only anime jobs`() = runBlocking {
        val mangaJobsRun = AtomicInteger(0)
        val animeJobsRun = AtomicInteger(0)

        val getLibraryManga = mockk<GetLibraryManga> {
            coEvery { await() } returns emptyList()
        }

        val getLibraryAnime = mockk<GetLibraryAnime> {
            coEvery { await() } returns listOf(
                createMockLibraryAnime(id = 1L),
                createMockLibraryAnime(id = 2L),
            )
        }

        val refreshMangaEnrichment = mockk<RefreshMangaEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                mangaJobsRun.incrementAndGet()
                createMockEnrichedEntry(id = 1L)
            }
        }

        val refreshAnimeEnrichment = mockk<RefreshAnimeEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                animeJobsRun.incrementAndGet()
                createMockEnrichedEntry(id = 1L)
            }
        }

        val coordinator = BulkEnrichmentCoordinator(
            getLibraryManga = getLibraryManga,
            getLibraryAnime = getLibraryAnime,
            refreshMangaEnrichment = refreshMangaEnrichment,
            refreshAnimeEnrichment = refreshAnimeEnrichment,
        )

        coordinator.refreshAll(force = false)

        // No manga jobs should run
        assertEquals(0, mangaJobsRun.get())
        // Only anime jobs should run
        assertEquals(2, animeJobsRun.get())
    }

    @Test
    fun `refreshAll with both empty manga and anime lists completes without launching jobs`() = runBlocking {
        val mangaJobsRun = AtomicInteger(0)
        val animeJobsRun = AtomicInteger(0)

        val getLibraryManga = mockk<GetLibraryManga> {
            coEvery { await() } returns emptyList()
        }

        val getLibraryAnime = mockk<GetLibraryAnime> {
            coEvery { await() } returns emptyList()
        }

        val refreshMangaEnrichment = mockk<RefreshMangaEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                mangaJobsRun.incrementAndGet()
                createMockEnrichedEntry(id = 1L)
            }
        }

        val refreshAnimeEnrichment = mockk<RefreshAnimeEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                animeJobsRun.incrementAndGet()
                createMockEnrichedEntry(id = 1L)
            }
        }

        val coordinator = BulkEnrichmentCoordinator(
            getLibraryManga = getLibraryManga,
            getLibraryAnime = getLibraryAnime,
            refreshMangaEnrichment = refreshMangaEnrichment,
            refreshAnimeEnrichment = refreshAnimeEnrichment,
        )

        // Should complete without error and without launching any jobs
        coordinator.refreshAll(force = false)

        assertEquals(0, mangaJobsRun.get())
        assertEquals(0, animeJobsRun.get())
    }

    @Test
    fun `refreshAll with large concurrent batch completes all jobs without deadlock`() = runBlocking {
        val jobCompletionCount = AtomicInteger(0)
        val jobCount = 25

        val getLibraryManga = mockk<GetLibraryManga> {
            coEvery { await() } returns (1..jobCount).map { id ->
                createMockLibraryManga(id = id.toLong())
            }
        }

        val getLibraryAnime = mockk<GetLibraryAnime> {
            coEvery { await() } returns (1..jobCount).map { id ->
                createMockLibraryAnime(id = id.toLong())
            }
        }

        val refreshMangaEnrichment = mockk<RefreshMangaEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                jobCompletionCount.incrementAndGet()
                delay(5) // Minimal delay to simulate work
                createMockEnrichedEntry(id = 1L)
            }
        }

        val refreshAnimeEnrichment = mockk<RefreshAnimeEnrichment> {
            coEvery { await(any(), any(), any()) } coAnswers {
                jobCompletionCount.incrementAndGet()
                delay(5) // Minimal delay to simulate work
                createMockEnrichedEntry(id = 1L)
            }
        }

        val coordinator = BulkEnrichmentCoordinator(
            getLibraryManga = getLibraryManga,
            getLibraryAnime = getLibraryAnime,
            refreshMangaEnrichment = refreshMangaEnrichment,
            refreshAnimeEnrichment = refreshAnimeEnrichment,
        )

        // Should complete all 50 jobs (25 manga + 25 anime) without deadlock
        coordinator.refreshAll(force = false)

        // Verify all jobs completed successfully
        assertEquals(jobCount * 2, jobCompletionCount.get())
    }

    // Test helper functions
    private fun createMockLibraryManga(id: Long): LibraryManga {
        return LibraryManga(
            manga = mockk {
                every { this@mockk.id } returns id
                every { title } returns "Manga $id"
            },
            category = 1L,
            totalChapters = 100L,
            readCount = 50L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    private fun createMockLibraryAnime(id: Long): LibraryAnime {
        return LibraryAnime(
            anime = mockk {
                every { this@mockk.id } returns id
                every { title } returns "Anime $id"
            },
            category = 1L,
            totalCount = 12L,
            seenCount = 6L,
            bookmarkCount = 0L,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }

    private fun createMockEnrichedEntry(id: Long): EnrichedEntry {
        return EnrichedEntry(
            entryId = id,
            mediaType = EnrichmentMediaType.MANGA,
            mergedTitle = "Test Entry $id",
            compositeScore = 0.5,
            confidenceLabel = "Medium",
            sourceCoverage = listOf("AniList"),
            summary = "Test summary",
            recommendations = emptyList(),
            failures = emptyList(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000L,
        )
    }
}
