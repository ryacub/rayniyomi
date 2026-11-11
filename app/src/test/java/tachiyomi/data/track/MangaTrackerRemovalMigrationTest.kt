package tachiyomi.data.track

import app.cash.sqldelight.db.QueryResult
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class MangaTrackerRemovalMigrationTest {

    private fun InMemoryMangaDb.insertTrack(mangaId: Long, trackerId: Long) = driver.execute(
        null,
        "INSERT INTO manga_sync(manga_id, sync_id, remote_id, title, last_chapter_read, total_chapters, " +
            "status, score, remote_url, start_date, finish_date) " +
            "VALUES ($mangaId, $trackerId, 1, 'title', 0, 0, 0, 0, '', 0, 0)",
        0,
    )

    private fun InMemoryMangaDb.trackRows(): List<Pair<Long, Long>> = driver.executeQuery(
        identifier = null,
        sql = "SELECT manga_id, sync_id FROM manga_sync ORDER BY manga_id, sync_id",
        mapper = { cursor ->
            val rows = mutableListOf<Pair<Long, Long>>()
            while (cursor.next().value) rows += cursor.getLong(0)!! to cursor.getLong(1)!!
            QueryResult.Value(rows)
        },
        parameters = 0,
    ).value

    @Test
    fun `the manga migration deletes Suwayomi tracks and keeps other trackers`() {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "manga-a", title = "A")
            db.insertManga(id = 2, url = "manga-b", title = "B")
            db.insertTrack(mangaId = 1, trackerId = 9)
            db.insertTrack(mangaId = 1, trackerId = 1)
            db.insertTrack(mangaId = 2, trackerId = 9)

            Database.Schema.migrate(db.driver, oldVersion = 40, newVersion = 41)

            assertEquals(listOf(1L to 1L), db.trackRows())
        }
    }
}
