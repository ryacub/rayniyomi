package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.di.testAppGraph
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class ExternalIntentsInitTest {
    private val anime = Anime.create().copy(id = 1L, source = 10L)
    private val episode = Episode.create().copy(id = 2L, animeId = 1L)

    @BeforeEach
    fun setUp() {
        AppGraphHolder.graphOrNull = null
        val graph = testAppGraph
        coEvery { graph.getAnime.await(anime.id) } returns anime
        every { graph.animeSourceManager.get(anime.source) } returns mockk<AnimeSource>()
    }

    @AfterEach
    fun tearDown() {
        AppGraphHolder.graphOrNull = null
        unmockkAll()
    }

    @Test
    fun `initAnime loads the episode by id without reading the episode list`() = runTest {
        coEvery { testAppGraph.getEpisode.await(episode.id) } returns episode
        val external = ExternalIntents()

        assertTrue(external.initAnime(anime.id, episode.id))

        assertEquals(episode, external.episode)
        coVerify(exactly = 0) { testAppGraph.getEpisodesByAnimeId.await(any()) }
    }

    @Test
    fun `initAnime rejects an episode of another anime`() = runTest {
        coEvery { testAppGraph.getEpisode.await(episode.id) } returns episode.copy(animeId = 99L)
        coEvery { testAppGraph.getEpisodesByAnimeId.await(anime.id) } returns emptyList()
        val external = ExternalIntents()

        assertFalse(external.initAnime(anime.id, episode.id))
    }
}
