package tachiyomi.domain.entries.manga.interactor

import io.kotest.matchers.collections.shouldBeEmpty
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

    private val mangaRepository: MangaRepository = mockk(relaxed = true)
    private val getMangaTracks: GetMangaTracks = mockk(relaxed = true)
    private val interactor = GetDuplicateLibraryManga(mangaRepository, getMangaTracks)

    private fun manga(id: Long, title: String, favorite: Boolean = true): Manga =
        Manga.create().copy(id = id, title = title, favorite = favorite)

    private fun track(mangaId: Long, trackerId: Long = 1L, remoteId: Long = 100L): MangaTrack =
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

    private fun library(favorites: List<Manga>, tracks: List<MangaTrack> = emptyList()) {
        coEvery { mangaRepository.getMangaFavorites() } returns favorites
        coEvery { getMangaTracks.awaitAll() } returns tracks
        coEvery { getMangaTracks.await(any()) } answers { tracks.filter { it.mangaId == firstArg<Long>() } }
    }

    @Test
    fun `normalized title match is MEDIUM`() = runTest {
        val subject = manga(1, "The Re:Zero", favorite = false)
        library(listOf(manga(2, "Re Zero")))

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(2L to DuplicateConfidence.MEDIUM)
    }

    @Test
    fun `exact title match is HIGH`() = runTest {
        val subject = manga(1, "One Piece", favorite = false)
        library(listOf(manga(2, "ONE PIECE")))

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(2L to DuplicateConfidence.HIGH)
    }

    @Test
    fun `a title with no letters or digits matches nothing`() = runTest {
        val subject = manga(1, "...", favorite = false)
        library(listOf(manga(2, "???"), manga(3, "!!!")))

        interactor.awaitAll(subject).shouldBeEmpty()
    }

    @Test
    fun `every library entry on the same tracker remote id is reported`() = runTest {
        val subject = manga(1, "Subject", favorite = false)
        library(
            favorites = listOf(manga(2, "Other A"), manga(3, "Other B")),
            tracks = listOf(track(mangaId = 1), track(mangaId = 2), track(mangaId = 3)),
        )

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(
            2L to DuplicateConfidence.TRACKER,
            3L to DuplicateConfidence.TRACKER,
        )
    }

    @Test
    fun `tracker match wins over title match for the same entry`() = runTest {
        val subject = manga(1, "Bleach", favorite = false)
        library(
            favorites = listOf(manga(2, "Bleach")),
            tracks = listOf(track(mangaId = 1, remoteId = 99), track(mangaId = 2, remoteId = 99)),
        )

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(2L to DuplicateConfidence.TRACKER)
    }

    @Test
    fun `tracks with no remote id do not match`() = runTest {
        val subject = manga(1, "Subject", favorite = false)
        library(
            favorites = listOf(manga(2, "Other")),
            tracks = listOf(track(mangaId = 1, remoteId = 0), track(mangaId = 2, remoteId = 0)),
        )

        interactor.awaitAll(subject).shouldBeEmpty()
    }

    @Test
    fun `an entry in the library is not its own duplicate`() = runTest {
        val subject = manga(1, "Dragon Ball")
        library(listOf(subject), tracks = listOf(track(mangaId = 1)))

        interactor.awaitAll(subject).shouldBeEmpty()
    }
}
