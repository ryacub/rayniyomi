package eu.kanade.domain.items.episode.interactor

import eu.kanade.tachiyomi.data.filler.AnimeFillerSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.library.service.LibraryPreferences

class PopulateFillerMarksTest {

    @Test
    fun `disabled preference skips filler source`() = runTest {
        val source = mockk<AnimeFillerSource>()
        val updateEpisode = mockk<UpdateEpisode>(relaxed = true)
        val interactor = PopulateFillerMarks(
            source = source,
            updateEpisode = updateEpisode,
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
        )

        interactor.await(anime(), episodes())

        coVerify(exactly = 0) { source.getFillerEpisodes(any()) }
        coVerify(exactly = 0) { updateEpisode.awaitAll(any()) }
    }

    @Test
    fun `matched lookup marks only exact unmarked episodes`() = runTest {
        val source = mockk<AnimeFillerSource>()
        val updateEpisode = mockk<UpdateEpisode>(relaxed = true)
        val preferences = LibraryPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        key = "auto_populate_anime_fillermarks",
                        data = true,
                        defaultValue = false,
                    ),
                ),
            ),
        )
        assertTrue(preferences.autoPopulateAnimeFillermarks().get())
        coEvery { source.getFillerEpisodes("Naruto") } returns setOf(2.0, 2.5)
        val interactor = PopulateFillerMarks(source, updateEpisode, preferences)

        interactor.await(anime(), episodes())

        coVerify { source.getFillerEpisodes("Naruto") }
        coVerify {
            updateEpisode.awaitAll(
                listOf(
                    EpisodeUpdate(id = 2L, fillermark = true),
                    EpisodeUpdate(id = 4L, fillermark = true),
                ),
            )
        }
    }

    private fun anime() = Anime.create().copy(id = 10L, title = "Naruto")

    private fun episodes() = listOf(
        Episode.create().copy(id = 1L, animeId = 10L, episodeNumber = 1.0),
        Episode.create().copy(id = 2L, animeId = 10L, episodeNumber = 2.0),
        Episode.create().copy(id = 3L, animeId = 10L, episodeNumber = 3.0, fillermark = true),
        Episode.create().copy(id = 4L, animeId = 10L, episodeNumber = 2.5),
    )
}
