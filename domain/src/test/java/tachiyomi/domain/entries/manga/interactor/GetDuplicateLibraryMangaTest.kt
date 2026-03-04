package tachiyomi.domain.entries.manga.interactor

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.manga.model.DuplicateConfidence
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

@Execution(ExecutionMode.CONCURRENT)
class GetDuplicateLibraryMangaTest {

    private val mangaRepository: MangaRepository = mockk()
    private val getMangaTracks: GetMangaTracks = mockk()
    private val interactor = GetDuplicateLibraryManga(mangaRepository, getMangaTracks)

    private fun createManga(id: Long = 1L, title: String = "Test Manga"): Manga =
        Manga.create().copy(id = id, title = title)

    private fun createTrack(mangaId: Long, trackerId: Long = 1L, remoteId: Long = 100L): MangaTrack =
        MangaTrack(
            id = mangaId * 10 + trackerId,
            mangaId = mangaId,
            trackerId = trackerId,
            remoteId = remoteId,
            libraryId = null,
            title = "",
            lastChapterRead = 0.0,
            totalChapters = 0L,
            status = 0L,
            score = 0.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )

    @Test
    fun `awaitAll returns TRACKER confidence when tracker ID matches`() = runTest {
        val manga = createManga(id = 1L, title = "One Piece")
        val duplicate = createManga(id = 2L, title = "One Piece (MangaDex)")
        val track = createTrack(mangaId = 1L, trackerId = 1L, remoteId = 42L)

        coEvery { getMangaTracks.await(1L) } returns listOf(track)
        coEvery { mangaRepository.getDuplicateLibraryMangaByTracker(1L, 42L, 1L) } returns listOf(duplicate)
        coEvery { mangaRepository.getDuplicateLibraryManga(1L, "one piece") } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryMangaByNormalizedTitle(any(), 1L) } returns emptyList()

        val result = interactor.awaitAll(manga)

        result shouldHaveSize 1
        result[0].confidence shouldBe DuplicateConfidence.TRACKER
        result[0].loser.id shouldBe 2L
    }

    @Test
    fun `awaitAll returns HIGH confidence when exact title matches`() = runTest {
        val manga = createManga(id = 1L, title = "Naruto")
        val duplicate = createManga(id = 2L, title = "Naruto")

        coEvery { getMangaTracks.await(1L) } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryManga(1L, "naruto") } returns listOf(duplicate)
        coEvery { mangaRepository.getDuplicateLibraryMangaByNormalizedTitle(any(), 1L) } returns emptyList()

        val result = interactor.awaitAll(manga)

        result shouldHaveSize 1
        result[0].confidence shouldBe DuplicateConfidence.HIGH
        result[0].loser.id shouldBe 2L
    }

    @Test
    fun `awaitAll returns MEDIUM confidence when normalized title matches`() = runTest {
        // TitleNormalizer replaces punctuation with space: "Re:Zero" -> "re zero"
        val manga = createManga(id = 1L, title = "Re:Zero")
        val duplicate = createManga(id = 2L, title = "Re Zero")

        coEvery { getMangaTracks.await(1L) } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryManga(1L, "re:zero") } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryMangaByNormalizedTitle("re zero", 1L) } returns listOf(duplicate)

        val result = interactor.awaitAll(manga)

        result shouldHaveSize 1
        result[0].confidence shouldBe DuplicateConfidence.MEDIUM
        result[0].loser.id shouldBe 2L
    }

    @Test
    fun `awaitAll returns empty list when no duplicates found`() = runTest {
        val manga = createManga(id = 1L, title = "Unique Title")

        coEvery { getMangaTracks.await(1L) } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryManga(1L, "unique title") } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryMangaByNormalizedTitle("unique title", 1L) } returns emptyList()

        val result = interactor.awaitAll(manga)

        result.shouldBeEmpty()
    }

    @Test
    fun `awaitAll deduplicates when same entry appears in multiple tiers`() = runTest {
        val manga = createManga(id = 1L, title = "Bleach")
        val duplicate = createManga(id = 2L, title = "Bleach")
        val track = createTrack(mangaId = 1L, trackerId = 1L, remoteId = 99L)

        coEvery { getMangaTracks.await(1L) } returns listOf(track)
        coEvery { mangaRepository.getDuplicateLibraryMangaByTracker(1L, 99L, 1L) } returns listOf(duplicate)
        coEvery { mangaRepository.getDuplicateLibraryManga(1L, "bleach") } returns listOf(duplicate)
        coEvery { mangaRepository.getDuplicateLibraryMangaByNormalizedTitle("bleach", 1L) } returns listOf(duplicate)

        val result = interactor.awaitAll(manga)

        // Should only appear once — the highest confidence (TRACKER) wins
        result shouldHaveSize 1
        result[0].confidence shouldBe DuplicateConfidence.TRACKER
    }

    @Test
    fun `awaitAll skips tracks with zero remoteId`() = runTest {
        val manga = createManga(id = 1L, title = "Dragon Ball")
        val trackWithZeroId = createTrack(mangaId = 1L, trackerId = 1L, remoteId = 0L)

        coEvery { getMangaTracks.await(1L) } returns listOf(trackWithZeroId)
        coEvery { mangaRepository.getDuplicateLibraryManga(1L, "dragon ball") } returns emptyList()
        coEvery { mangaRepository.getDuplicateLibraryMangaByNormalizedTitle("dragon ball", 1L) } returns emptyList()

        val result = interactor.awaitAll(manga)

        result.shouldBeEmpty()
    }
}
