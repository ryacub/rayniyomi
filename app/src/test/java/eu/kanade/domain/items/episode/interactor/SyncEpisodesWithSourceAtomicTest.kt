package eu.kanade.domain.items.episode.interactor

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.library.service.LibraryPreferences

class SyncEpisodesWithSourceAtomicTest {

    @Test
    fun `a failed insert keeps the episodes that the sync was replacing`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1, url = "/series", title = "Series")
            db.insertEpisode(id = 10, animeId = 1, url = "/old")
            db.driver.execute(null, "UPDATE episodes SET seen = 1, bookmark = 1 WHERE _id = 10", 0)
            db.driver.execute(
                null,
                "CREATE TRIGGER fail_insert BEFORE INSERT ON episodes WHEN NEW.url = '/new' " +
                    "BEGIN SELECT RAISE(ABORT, 'insert failed'); END",
                0,
            )
            val repository = EpisodeRepositoryImpl(db.handler)
            val sync = SyncEpisodesWithSource(
                downloadManager = mockk(relaxed = true),
                downloadProvider = mockk(relaxed = true),
                episodeRepository = repository,
                shouldUpdateDbEpisode = ShouldUpdateDbEpisode(),
                updateAnime = mockk(relaxed = true),
                getEpisodesByAnimeId = GetEpisodesByAnimeId(repository),
                libraryPreferences = mockk<LibraryPreferences> {
                    every { markDuplicateSeenEpisodeAsSeen().get() } returns emptySet()
                },
            )
            val renamed = SEpisode.create().apply {
                url = "/new"
                name = "Episode 1"
                episode_number = 1f
            }

            val result = runCatching {
                sync.await(
                    listOf(renamed),
                    Anime.create().copy(id = 1, title = "Series"),
                    mockk<AnimeSource> { every { id } returns 1 },
                )
            }

            val stored = repository.getEpisodeByAnimeId(1)
            assertEquals(listOf("/old"), stored.map { it.url })
            assertTrue(stored.single().seen && stored.single().bookmark)
            assertTrue(result.isFailure)
        }
    }
}
