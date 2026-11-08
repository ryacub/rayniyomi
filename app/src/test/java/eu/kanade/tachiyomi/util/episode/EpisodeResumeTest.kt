package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class EpisodeResumeTest {

    private val previousGraph = AppGraphHolder.graphOrNull

    @BeforeEach
    fun setUp() {
        AppGraphHolder.graph = mockk<AppGraph> {
            every { basePreferences.downloadedOnly().get() } returns false
        }
    }

    @AfterEach
    fun tearDown() {
        AppGraphHolder.graphOrNull = previousGraph
    }

    @Test
    fun `untouched series has nothing to resume`() {
        val items = listOf(item(1.0), item(2.0))

        assertNull(items.resumeEpisode(anime(Anime.EPISODE_SORT_ASC)))
    }

    @Test
    fun `partly watched episode is the one to resume`() {
        val items = listOf(item(1.0, lastSecondSeen = 754_000L), item(2.0))

        assertEquals(1.0, items.resumeEpisode(anime(Anime.EPISODE_SORT_ASC))?.episodeNumber)
    }

    @Test
    fun `finished episode resumes at the next episode`() {
        val items = listOf(item(1.0, seen = true), item(2.0))

        assertEquals(2.0, items.resumeEpisode(anime(Anime.EPISODE_SORT_ASC))?.episodeNumber)
    }

    @Test
    fun `descending sort still resumes the lowest unseen episode`() {
        val items = listOf(item(3.0), item(2.0, lastSecondSeen = 60_000L), item(1.0, seen = true))

        assertEquals(2.0, items.resumeEpisode(anime(Anime.EPISODE_SORT_DESC))?.episodeNumber)
    }

    @Test
    fun `unseen filter that hides seen episodes keeps the episode to resume`() {
        val items = listOf(item(1.0, seen = true), item(2.0, lastSecondSeen = 5_000L))
        val anime = anime(Anime.EPISODE_SORT_ASC or Anime.EPISODE_SHOW_UNSEEN)

        assertEquals(2.0, items.resumeEpisode(anime)?.episodeNumber)
    }

    @Test
    fun `fully watched series has nothing to resume`() {
        val items = listOf(item(1.0, seen = true), item(2.0, seen = true))

        assertNull(items.resumeEpisode(anime(Anime.EPISODE_SORT_ASC)))
    }

    private fun anime(flags: Long) = Anime.create().copy(
        id = 1L,
        episodeFlags = flags or Anime.EPISODE_SORTING_NUMBER,
    )

    private fun item(number: Double, seen: Boolean = false, lastSecondSeen: Long = 0L) = EpisodeList.Item(
        episode = Episode.create().copy(
            id = number.toLong(),
            animeId = 1L,
            episodeNumber = number,
            seen = seen,
            lastSecondSeen = lastSecondSeen,
        ),
        downloadState = AnimeDownload.State.NOT_DOWNLOADED,
        downloadProgress = 0,
    )
}
