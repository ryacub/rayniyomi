package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.domain.items.episode.model.Episode

class PersistedEntityIdTest {

    @Test
    fun `inserting an episode returns the generated id`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1L, url = "anime", title = "Anime")

            val episode = Episode.create().copy(
                animeId = 1L,
                url = "episode",
                name = "Episode",
            )
            val persisted = EpisodeRepositoryImpl(db.handler)
                .addAllEpisodes(listOf(episode))
                .single()

            assertTrue(persisted.id > 0L)
        }
    }
}
