package tachiyomi.data.items.episode

import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeRepositoryWatchTimeTest {

    @Test
    fun `watch time counts total seconds for seen episodes and progress for unseen ones`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1, url = "/one", title = "One")
            db.insertAnime(id = 2, url = "/two", title = "Two")
            db.insertAnime(id = 3, url = "/three", title = "Three")
            db.insertEpisode(id = 10, animeId = 1, url = "/seen")
            db.insertEpisode(id = 11, animeId = 1, url = "/partial")
            db.insertEpisode(id = 20, animeId = 2, url = "/partial")
            db.driver.execute(
                null,
                "UPDATE episodes SET seen = 1, total_seconds = 1200, last_second_seen = 5 WHERE _id = 10",
                0,
            )
            db.driver.execute(
                null,
                "UPDATE episodes SET total_seconds = 1500, last_second_seen = 300 WHERE _id = 11",
                0,
            )
            db.driver.execute(null, "UPDATE episodes SET total_seconds = 900, last_second_seen = 60 WHERE _id = 20", 0)

            val watchTime = EpisodeRepositoryImpl(db.handler).getWatchTimeByAnimeId()

            assertEquals(mapOf(1L to 1500L, 2L to 60L), watchTime)
        }
    }
}
