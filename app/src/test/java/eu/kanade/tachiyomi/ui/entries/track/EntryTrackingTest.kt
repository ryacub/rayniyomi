package eu.kanade.tachiyomi.ui.entries.track

import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

class EntryTrackingTest {

    private val getTracks = mockk<GetAnimeTracks>()
    private val getAnime = mockk<GetAnime>()
    private val sourceManager = mockk<AnimeSourceManager>()
    private val source = mockk<AnimeSource>()

    private val tracking = AnimeEntryTracking(
        getTracks = getTracks,
        getAnime = getAnime,
        refreshTracks = mockk<RefreshAnimeTracks>(),
        sourceManager = sourceManager,
        deleteTrack = mockk<DeleteAnimeTrack>(),
    )

    private val enhancedTracker = mockk<BaseTracker>(
        relaxed = true,
        moreInterfaces = arrayOf(AnimeTracker::class, EnhancedAnimeTracker::class),
    ).also { every { it.animeService } returns it as AnimeTracker }

    @Test
    fun `anime tracks arrive wrapped as anime entry tracks`() = runTest {
        val track = AnimeTrack(
            id = 1, animeId = 10, trackerId = 2, remoteId = 3, libraryId = null, title = "Show",
            lastEpisodeSeen = 4.0, totalEpisodes = 12, status = 1, score = 7.0, remoteUrl = "",
            startDate = 0, finishDate = 0, private = false,
        )
        every { getTracks.subscribe(10) } returns flowOf(listOf(track))

        tracking.subscribeTracks(10).first() shouldBe listOf(EntryTrack.Anime(track))
    }

    @Test
    fun `an enhanced tracker that rejects the source is not accepted`() {
        every { sourceManager.getOrStub(5) } returns source
        every { (enhancedTracker as EnhancedAnimeTracker).accept(source) } returns false

        tracking.accepts(enhancedTracker, 5) shouldBe false
    }

    @Test
    fun `a plain tracker is accepted for any source`() {
        every { sourceManager.getOrStub(5) } returns source
        val plainTracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(AnimeTracker::class))

        tracking.accepts(plainTracker, 5) shouldBe true
    }

    @Test
    fun `enhanced registration fails when the tracker finds no match`() = runTest {
        val anime = Anime.create().copy(id = 10)
        coEvery { getAnime.await(10) } returns anime
        coEvery { (enhancedTracker as EnhancedAnimeTracker).match(anime) } returns null

        assertThrows<Exception> { tracking.registerEnhanced(enhancedTracker, 10) }
    }

    @Test
    fun `enhanced registration does nothing when the anime is gone`() = runTest {
        coEvery { getAnime.await(10) } returns null

        tracking.registerEnhanced(enhancedTracker, 10)

        coVerify(exactly = 0) { (enhancedTracker as EnhancedAnimeTracker).match(any()) }
        coVerify(exactly = 0) { (enhancedTracker as AnimeTracker).register(any(), any()) }
    }

    @Test
    fun `search results arrive wrapped as anime search results`() = runTest {
        val result = AnimeTrackSearch.create(2)
        coEvery { (enhancedTracker as AnimeTracker).searchAnime("show") } returns listOf(result)

        tracking.search(enhancedTracker, "show") shouldBe listOf(EntryTrackSearch.Anime(result))
    }
}
