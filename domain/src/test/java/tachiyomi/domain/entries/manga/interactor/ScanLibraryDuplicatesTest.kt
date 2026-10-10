package tachiyomi.domain.entries.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.DuplicateConfidence
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

class ScanLibraryDuplicatesTest {

    private val mangaRepository: MangaRepository = mockk(relaxed = true)
    private val getMangaTracks: GetMangaTracks = mockk(relaxed = true)
    private val getLibraryManga: GetLibraryManga = mockk()
    private val scan = ScanLibraryDuplicates(
        getLibraryManga = getLibraryManga,
        getDuplicateLibraryManga = GetDuplicateLibraryManga(mangaRepository, getMangaTracks),
    )

    private fun manga(id: Long, title: String) = Manga.create().copy(id = id, title = title, favorite = true)

    private fun row(manga: Manga, category: Long) = LibraryManga(
        manga = manga,
        category = category,
        totalChapters = 0,
        readCount = 0,
        bookmarkCount = 0,
        latestUpload = 0,
        chapterFetchedAt = 0,
        lastRead = 0,
    )

    @Test
    fun `scan reports each pair once with the earlier entry as winner and no query per entry`() = runTest {
        val naruto = manga(1, "Naruto")
        val narutoCopy = manga(2, "NARUTO")
        val bleach = manga(3, "Bleach")
        val favorites = listOf(naruto, narutoCopy, bleach)
        coEvery { getLibraryManga.await() } returns listOf(
            row(naruto, category = 1),
            row(narutoCopy, category = 1),
            row(naruto, category = 2),
            row(bleach, category = 1),
        )
        coEvery { mangaRepository.getMangaFavorites() } returns favorites
        coEvery { getMangaTracks.awaitAll() } returns emptyList()
        coEvery { getMangaTracks.await(any()) } returns emptyList()

        val result = scan.await()

        result.map { Triple(it.winner.id, it.loser.id, it.confidence) } shouldBe listOf(
            Triple(1L, 2L, DuplicateConfidence.HIGH),
        )
        coVerify(exactly = 0) { mangaRepository.getDuplicateLibraryManga(any(), any()) }
    }
}
