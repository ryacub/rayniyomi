package tachiyomi.data.entries.anime

import app.cash.sqldelight.db.QueryResult
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase

class AnimelibViewTest {

    private val episodesTableAccess = Regex("""^(SCAN|SEARCH) (TABLE )?episodes\b""")

    @Test
    fun `the library view reads the episodes table once`() {
        InMemoryAnimeDb().use { db ->
            assertEquals(1, db.planRowsReadingEpisodes())
        }
    }

    @Test
    fun `the migration installs the library view that reads the episodes table once`() {
        InMemoryAnimeDb().use { db ->
            AnimeDatabase.Schema.migrate(db.driver, oldVersion = 139, newVersion = AnimeDatabase.Schema.version)

            assertEquals(1, db.planRowsReadingEpisodes())
        }
    }

    @Test
    fun `the library view totals episodes, seasons, bookmarks, and history`() {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1, url = "/single", title = "Single")
            db.insertEpisode(id = 11, animeId = 1, url = "/single/1")
            db.insertEpisode(id = 12, animeId = 1, url = "/single/2")
            db.exec("UPDATE episodes SET seen = 1, date_upload = 100, date_fetch = 200 WHERE _id = 11")
            db.exec("UPDATE episodes SET bookmark = 1, date_upload = 300, date_fetch = 150 WHERE _id = 12")
            db.insertHistory(id = 1, episodeId = 11, lastSeen = 500)

            db.insertAnime(id = 2, url = "/parent", title = "Parent")
            db.insertAnime(id = 3, url = "/parent/s1", title = "Season 1")
            db.insertAnime(id = 4, url = "/parent/s2", title = "Season 2")
            db.exec("UPDATE animes SET fetch_type = 0 WHERE _id = 2")
            db.exec("UPDATE animes SET favorite = 0, parent_id = 2 WHERE _id IN (3, 4)")
            db.insertEpisode(id = 31, animeId = 3, url = "/parent/s1/1")
            db.exec("UPDATE episodes SET seen = 1, date_upload = 50, date_fetch = 60 WHERE _id = 31")
            db.insertHistory(id = 2, episodeId = 31, lastSeen = 900)
            db.insertEpisode(id = 41, animeId = 4, url = "/parent/s2/1")
            db.exec("UPDATE episodes SET bookmark = 1, date_upload = 700, date_fetch = 800 WHERE _id = 41")

            db.insertAnime(id = 6, url = "/parent/empty", title = "Empty season")
            db.insertAnime(id = 7, url = "/parent/nested", title = "Nested parent")
            db.exec("UPDATE animes SET favorite = 0, parent_id = 2 WHERE _id IN (6, 7)")
            db.exec("UPDATE animes SET fetch_type = 0 WHERE _id = 7")

            db.insertAnime(id = 5, url = "/browsed", title = "Browsed")
            db.exec("UPDATE animes SET favorite = 0 WHERE _id = 5")
            db.insertEpisode(id = 51, animeId = 5, url = "/browsed/1")

            assertEquals(
                listOf(
                    listOf(1L, 2L, 1L, 300L, 200L, 500L, 1L),
                    listOf(2L, 4L, 2L, 700L, 800L, 900L, 1L),
                ),
                db.libraryRows(),
            )
        }
    }

    private fun InMemoryAnimeDb.exec(sql: String) = driver.execute(null, sql, 0)

    private fun InMemoryAnimeDb.planRowsReadingEpisodes(): Int {
        val details = mutableListOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "EXPLAIN QUERY PLAN SELECT * FROM animelibView",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) details += cursor.getString(3).orEmpty()
                QueryResult.Unit
            },
        )
        return details.count { episodesTableAccess.containsMatchIn(it) }
    }

    private fun InMemoryAnimeDb.libraryRows(): List<List<Long>> {
        val rows = mutableListOf<List<Long>>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT _id, totalCount, seenCount, latestUpload, episodeFetchedAt, lastSeen, bookmarkCount " +
                "FROM animelibView ORDER BY _id",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) rows += (0..6).map { cursor.getLong(it)!! }
                QueryResult.Unit
            },
        )
        return rows
    }
}
