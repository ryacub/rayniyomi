package eu.kanade.tachiyomi.ui.entries.track

import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import java.io.IOException

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
    fun `enhanced registration reports no match when the tracker finds none`() = runTest {
        val anime = Anime.create().copy(id = 10)
        coEvery { getAnime.await(10) } returns anime
        coEvery { (enhancedTracker as EnhancedAnimeTracker).match(anime) } returns null

        tracking.registerEnhanced(enhancedTracker, 10) shouldBe EnhancedRegistration.NO_MATCH
    }

    @Test
    fun `enhanced registration reports no match when the tracker fails`() = runTest {
        val anime = Anime.create().copy(id = 10)
        coEvery { getAnime.await(10) } returns anime
        coEvery { (enhancedTracker as EnhancedAnimeTracker).match(anime) } throws IOException("offline")

        tracking.registerEnhanced(enhancedTracker, 10) shouldBe EnhancedRegistration.NO_MATCH
    }

    @Test
    fun `enhanced registration registers the match`() = runTest {
        val anime = Anime.create().copy(id = 10)
        val match = AnimeTrackSearch.create(2)
        coEvery { getAnime.await(10) } returns anime
        coEvery { (enhancedTracker as EnhancedAnimeTracker).match(anime) } returns match

        tracking.registerEnhanced(enhancedTracker, 10) shouldBe EnhancedRegistration.REGISTERED
        coVerify { (enhancedTracker as AnimeTracker).register(match, 10) }
    }

    @Test
    fun `enhanced registration does nothing when the anime is gone`() = runTest {
        coEvery { getAnime.await(10) } returns null

        tracking.registerEnhanced(enhancedTracker, 10) shouldBe EnhancedRegistration.ENTRY_MISSING
        coVerify(exactly = 0) { (enhancedTracker as EnhancedAnimeTracker).match(any()) }
    }

    @Test
    fun `manga tracking supports only manga trackers`() {
        val mangaTracking = mangaTracking()
        val animeOnly = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(AnimeTracker::class))
        val mangaOnly = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(MangaTracker::class))

        mangaTracking.supports(animeOnly) shouldBe false
        mangaTracking.supports(mangaOnly) shouldBe true
        tracking.supports(mangaOnly) shouldBe false
    }

    @Test
    fun `manga tracking treats only enhanced manga trackers as enhanced`() {
        mangaTracking().isEnhanced(enhancedTracker) shouldBe false
        tracking.isEnhanced(enhancedTracker) shouldBe true
    }

    @Test
    fun `manga tracking rejects a source that an enhanced manga tracker refuses`() {
        val mangaSourceManager = mockk<MangaSourceManager>()
        val mangaSource = mockk<MangaSource>()
        val enhancedMangaTracker = mockk<BaseTracker>(
            relaxed = true,
            moreInterfaces = arrayOf(MangaTracker::class, EnhancedMangaTracker::class),
        )
        every { mangaSourceManager.getOrStub(5) } returns mangaSource
        every { (enhancedMangaTracker as EnhancedMangaTracker).accept(mangaSource) } returns false

        mangaTracking(sourceManager = mangaSourceManager).accepts(enhancedMangaTracker, 5) shouldBe false
    }

    @Test
    fun `search results arrive wrapped as anime search results`() = runTest {
        val result = AnimeTrackSearch.create(2)
        coEvery { (enhancedTracker as AnimeTracker).searchAnime("show") } returns listOf(result)

        tracking.search(enhancedTracker, "show") shouldBe listOf(EntryTrackSearch.Anime(result))
    }

    private fun mangaTracking(sourceManager: MangaSourceManager = mockk()) = MangaEntryTracking(
        getTracks = mockk<GetMangaTracks>(),
        getManga = mockk<GetManga>(),
        refreshTracks = mockk<RefreshMangaTracks>(),
        sourceManager = sourceManager,
        deleteTrack = mockk<DeleteMangaTrack>(),
    )
}
