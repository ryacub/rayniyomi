package tachiyomi.domain.entries.anime.interactor

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.DuplicateConfidence
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

@Execution(ExecutionMode.CONCURRENT)
class GetDuplicateLibraryAnimeTest {

    private val animeRepository: AnimeRepository = mockk(relaxed = true)
    private val getAnimeTracks: GetAnimeTracks = mockk(relaxed = true)
    private val interactor = GetDuplicateLibraryAnime(animeRepository, getAnimeTracks)

    private fun anime(id: Long, title: String, favorite: Boolean = true): Anime =
        Anime.create().copy(id = id, title = title, favorite = favorite)

    private fun track(animeId: Long, trackerId: Long = 1L, remoteId: Long = 100L): AnimeTrack =
        AnimeTrack(
            id = animeId * 10 + trackerId,
            animeId = animeId,
            trackerId = trackerId,
            remoteId = remoteId,
            libraryId = null,
            title = "",
            lastEpisodeSeen = 0.0,
            totalEpisodes = 0L,
            status = 0L,
            score = 0.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )

    private fun library(favorites: List<Anime>, tracks: List<AnimeTrack> = emptyList()) {
        coEvery { animeRepository.getAnimeFavorites() } returns favorites
        coEvery { getAnimeTracks.awaitAll() } returns tracks
        coEvery { getAnimeTracks.await(any()) } answers { tracks.filter { it.animeId == firstArg<Long>() } }
    }

    @Test
    fun `normalized title match is MEDIUM`() = runTest {
        val subject = anime(1, "The Re:Zero", favorite = false)
        library(listOf(anime(2, "Re Zero")))

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(2L to DuplicateConfidence.MEDIUM)
    }

    @Test
    fun `exact title match is HIGH`() = runTest {
        val subject = anime(1, "One Piece", favorite = false)
        library(listOf(anime(2, "ONE PIECE")))

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(2L to DuplicateConfidence.HIGH)
    }

    @Test
    fun `a title with no letters or digits matches nothing`() = runTest {
        val subject = anime(1, "...", favorite = false)
        library(listOf(anime(2, "???"), anime(3, "!!!")))

        interactor.awaitAll(subject).shouldBeEmpty()
    }

    @Test
    fun `every library entry on the same tracker remote id is reported`() = runTest {
        val subject = anime(1, "Subject", favorite = false)
        library(
            favorites = listOf(anime(2, "Other A"), anime(3, "Other B")),
            tracks = listOf(track(animeId = 1), track(animeId = 2), track(animeId = 3)),
        )

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(
            2L to DuplicateConfidence.TRACKER,
            3L to DuplicateConfidence.TRACKER,
        )
    }

    @Test
    fun `tracker match wins over title match for the same entry`() = runTest {
        val subject = anime(1, "Bleach", favorite = false)
        library(
            favorites = listOf(anime(2, "Bleach")),
            tracks = listOf(track(animeId = 1, remoteId = 99), track(animeId = 2, remoteId = 99)),
        )

        val result = interactor.awaitAll(subject)

        result.map { it.loser.id to it.confidence } shouldBe listOf(2L to DuplicateConfidence.TRACKER)
    }

    @Test
    fun `tracks with no remote id do not match`() = runTest {
        val subject = anime(1, "Subject", favorite = false)
        library(
            favorites = listOf(anime(2, "Other")),
            tracks = listOf(track(animeId = 1, remoteId = 0), track(animeId = 2, remoteId = 0)),
        )

        interactor.awaitAll(subject).shouldBeEmpty()
    }

    @Test
    fun `an entry in the library is not its own duplicate`() = runTest {
        val subject = anime(1, "Dragon Ball")
        library(listOf(subject), tracks = listOf(track(animeId = 1)))

        interactor.awaitAll(subject).shouldBeEmpty()
    }
}
