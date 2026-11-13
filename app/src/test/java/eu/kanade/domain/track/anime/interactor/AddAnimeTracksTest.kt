package eu.kanade.domain.track.anime.interactor

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode

class AddAnimeTracksTest {

    @ParameterizedTest(name = "with unnumbered episode = {0}")
    @ValueSource(booleans = [false, true])
    fun `bind sends progress up to the first incomplete numbered episode`(includeUnnumbered: Boolean) = runTest {
        val entryId = 10L
        val numbered = (1..5).map { number ->
            Episode.create().copy(
                id = number.toLong(),
                animeId = entryId,
                episodeNumber = number.toDouble(),
                seen = number != 4,
            )
        }
        val items = if (includeUnnumbered) numbered + Episode.create().copy(animeId = entryId) else numbered
        val getItems = mockk<GetEpisodesByAnimeId>()
        coEvery { getItems.await(entryId) } returns items
        val track = AnimeTrack.create(2L).apply {
            anime_id = entryId
            title = "Title"
            started_watching_date = 1L
        }
        val tracker = mockk<AnimeTracker>()
        coEvery { tracker.bind(track, true) } returns track
        coEvery { tracker.setRemoteLastEpisodeSeen(any(), any()) } returns Unit
        val addTracks = AddAnimeTracks(
            mockk(relaxUnitFun = true),
            mockk<SyncEpisodeProgressWithTrack>(relaxUnitFun = true),
            getItems,
            mockk(),
        )

        addTracks.bind(tracker, track, entryId)

        coVerify(exactly = 1) {
            tracker.setRemoteLastEpisodeSeen(match { it.last_episode_seen == 3.0 }, 3)
        }
    }
}
