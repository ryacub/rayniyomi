package eu.kanade.tachiyomi.ui.entries.track

import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack

class EntryTrackTest {

    private val animeTrack = AnimeTrack(
        id = 1, animeId = 10, trackerId = 2, remoteId = 3, libraryId = null, title = "Show",
        lastEpisodeSeen = 4.0, totalEpisodes = 12, status = 1, score = 7.0, remoteUrl = "https://a",
        startDate = 100, finishDate = 200, private = true,
    )

    private val mangaTrack = MangaTrack(
        id = 1, mangaId = 20, trackerId = 2, remoteId = 3, libraryId = null, title = "Book",
        lastChapterRead = 6.0, totalChapters = 30, status = 1, score = 0.0, remoteUrl = "https://m",
        startDate = 0, finishDate = 0, private = false,
    )

    private val animeTracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(AnimeTracker::class)).also {
        every { it.animeService } returns it as AnimeTracker
    }

    private val mangaTracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(MangaTracker::class)).also {
        every { it.mangaService } returns it as MangaTracker
    }

    @Test
    fun `anime track reads episode progress and the anime id`() {
        val track = EntryTrack.Anime(animeTrack)

        track.entryId shouldBe 10
        track.progress shouldBe 4.0
        track.total shouldBe 12
        track.score shouldBe 7.0
    }

    @Test
    fun `manga track reads chapter progress and the manga id`() {
        val track = EntryTrack.Manga(mangaTrack)

        track.entryId shouldBe 20
        track.progress shouldBe 6.0
        track.total shouldBe 30
    }

    @Test
    fun `anime progress update sets the last seen episode`() = runTest {
        animeTracker.setRemoteProgress(EntryTrack.Anime(animeTrack), 5)

        coVerify {
            (animeTracker as AnimeTracker).setRemoteLastEpisodeSeen(
                match { it.anime_id == 10L && it.tracker_id == 2L && it.last_episode_seen == 4.0 },
                5,
            )
        }
    }

    @Test
    fun `manga progress update sets the last read chapter`() = runTest {
        mangaTracker.setRemoteProgress(EntryTrack.Manga(mangaTrack), 7)

        coVerify {
            (mangaTracker as MangaTracker).setRemoteLastChapterRead(
                match { it.manga_id == 20L && it.last_chapter_read == 6.0 },
                7,
            )
        }
    }

    @Test
    fun `status name is null when the tracker does not support the media type`() {
        mangaTracker.statusName(EntryTrack.Anime(animeTrack), 1) shouldBe null
    }

    @Test
    fun `register sends an anime search result to the anime service`() = runTest {
        val search = AnimeTrackSearch.create(2)

        animeTracker.register(EntryTrackSearch.Anime(search), 10)

        coVerify { (animeTracker as AnimeTracker).register(search, 10) }
    }

    @Test
    fun `private flag writes through to the wrapped search result`() {
        val search = MangaTrackSearch.create(2)

        EntryTrackSearch.Manga(search).private = true

        search.private shouldBe true
    }
}
