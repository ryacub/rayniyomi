package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime

/**
 * R795: two library entries can hold the same episode URL. The restore must resolve each
 * history record inside its owning anime.
 */
class AnimeRestorerHistoryTest {

    private fun restorer(db: InMemoryAnimeDb) = AnimeRestorer(
        handler = db.handler,
        getCategories = mockk(relaxed = true),
        getAnimeByUrlAndSourceId = mockk(relaxed = true),
        getEpisodesByAnimeId = mockk(relaxed = true),
        updateAnime = mockk(relaxed = true),
        getTracks = mockk(relaxed = true),
        insertTrack = mockk(relaxed = true),
        fetchInterval = mockk(relaxed = true),
    )

    private fun anime(id: Long) = Anime.create().copy(id = id)

    @Test
    fun `restore adds history to the owning anime when another anime shares the episode url`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1, url = "anime-a", title = "A")
            db.insertAnime(id = 2, url = "anime-b", title = "B")
            db.insertEpisode(id = 10, animeId = 1, url = "shared-episode")
            db.insertEpisode(id = 20, animeId = 2, url = "shared-episode")

            // Anime 2 already has history. Anime 1 has none.
            db.insertHistory(id = 100, episodeId = 20, lastSeen = 5_000)

            restorer(db).restoreHistory(
                anime(1),
                listOf(BackupAnimeHistory(url = "shared-episode", lastRead = 9_000)),
            )

            val rows = db.historyRows()
            assertEquals(2, rows.size, "the restore must add history instead of touching anime 2")

            val anime2History = rows.single { it.first == 100L }
            assertEquals(20L, anime2History.second)
            assertEquals(5_000L, anime2History.third, "anime 2 history must not change")

            val anime1History = rows.single { it.first != 100L }
            assertEquals(10L, anime1History.second, "history must attach to the anime 1 episode")
            assertEquals(9_000L, anime1History.third)
        }
    }

    @Test
    fun `restore updates existing history only within the owning anime`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1, url = "anime-a", title = "A")
            db.insertAnime(id = 2, url = "anime-b", title = "B")
            db.insertEpisode(id = 10, animeId = 1, url = "shared-episode")
            db.insertEpisode(id = 20, animeId = 2, url = "shared-episode")
            db.insertHistory(id = 100, episodeId = 10, lastSeen = 1_000)
            db.insertHistory(id = 200, episodeId = 20, lastSeen = 5_000)

            restorer(db).restoreHistory(
                anime(1),
                listOf(BackupAnimeHistory(url = "shared-episode", lastRead = 9_000)),
            )

            val rows = db.historyRows()
            assertEquals(2, rows.size)
            assertEquals(9_000L, rows.single { it.first == 100L }.third, "anime 1 history advances")
            assertEquals(5_000L, rows.single { it.first == 200L }.third, "anime 2 history is untouched")
        }
    }

    @Test
    fun `restore tolerates a duplicated episode url inside one anime`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1, url = "anime-a", title = "A")
            // The same URL twice under ONE anime - scoping alone does not make this single-row.
            db.insertEpisode(id = 10, animeId = 1, url = "dupe")
            db.insertEpisode(id = 11, animeId = 1, url = "dupe")

            restorer(db).restoreHistory(
                anime(1),
                listOf(BackupAnimeHistory(url = "dupe", lastRead = 9_000)),
            )

            assertEquals(1, db.historyRows().size)
        }
    }
}
