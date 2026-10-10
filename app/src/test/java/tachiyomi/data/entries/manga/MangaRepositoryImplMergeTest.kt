package tachiyomi.data.entries.manga

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.Chapters
import data.History
import data.Mangas
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import java.util.Properties

class MangaRepositoryImplMergeTest {

    private val keepId = 1L
    private val deleteId = 2L

    private suspend fun MergeMangaDb.merge() = MangaRepositoryImpl(handler).mergeEntries(keepId, deleteId)

    @Test
    fun `a category on both entries leaves one category row`() = runTest {
        MergeMangaDb().use { db ->
            db.insertCategory(id = 1)
            db.insertMangaCategory(mangaId = keepId, categoryId = 1)
            db.insertMangaCategory(mangaId = deleteId, categoryId = 1)

            db.merge()

            assertEquals(listOf(1L), db.longs("SELECT category_id FROM mangas_categories WHERE manga_id = $keepId"))
        }
    }

    @Test
    fun `a bookmark on the deleted entry survives on the shared chapter`() = runTest {
        MergeMangaDb().use { db ->
            db.insertChapter(id = 10, mangaId = keepId, url = "c1", bookmark = false)
            db.insertChapter(id = 20, mangaId = deleteId, url = "c1", bookmark = true)

            db.merge()

            assertEquals(listOf(1L), db.longs("SELECT bookmark FROM chapters WHERE _id = 10"))
        }
    }

    @Test
    fun `history on both shared chapters is summed and keeps the latest read`() = runTest {
        MergeMangaDb().use { db ->
            db.insertChapter(id = 10, mangaId = keepId, url = "c1")
            db.insertChapter(id = 20, mangaId = deleteId, url = "c1")
            db.insertHistory(chapterId = 10, lastRead = 1_000, timeRead = 100)
            db.insertHistory(chapterId = 20, lastRead = 2_000, timeRead = 50)

            db.merge()

            assertEquals(listOf(150L), db.longs("SELECT time_read FROM history WHERE chapter_id = 10"))
            assertEquals(listOf(2_000L), db.longs("SELECT last_read FROM history WHERE chapter_id = 10"))
        }
    }

    @Test
    fun `history of the deleted entry moves to the shared chapter`() = runTest {
        MergeMangaDb().use { db ->
            db.insertChapter(id = 10, mangaId = keepId, url = "c1")
            db.insertChapter(id = 20, mangaId = deleteId, url = "c1")
            db.insertHistory(chapterId = 20, lastRead = 2_000, timeRead = 50)

            db.merge()

            assertEquals(listOf(50L), db.longs("SELECT time_read FROM history WHERE chapter_id = 10"))
            assertEquals(listOf(2_000L), db.longs("SELECT last_read FROM history WHERE chapter_id = 10"))
        }
    }

    @Test
    fun `duplicate category rows on the deleted entry arrive as one row`() = runTest {
        MergeMangaDb().use { db ->
            db.insertCategory(id = 1)
            db.insertMangaCategory(mangaId = deleteId, categoryId = 1)
            db.insertMangaCategory(mangaId = deleteId, categoryId = 1)

            db.merge()

            assertEquals(listOf(1L), db.longs("SELECT category_id FROM mangas_categories WHERE manga_id = $keepId"))
        }
    }

    @Test
    fun `a missing read date on the kept chapter takes the deleted entry's date`() = runTest {
        MergeMangaDb().use { db ->
            db.insertChapter(id = 10, mangaId = keepId, url = "c1")
            db.insertChapter(id = 20, mangaId = deleteId, url = "c1")
            db.insertHistory(chapterId = 10, lastRead = null, timeRead = 100)
            db.insertHistory(chapterId = 20, lastRead = 2_000, timeRead = 50)

            db.merge()

            assertEquals(listOf(2_000L), db.longs("SELECT last_read FROM history WHERE chapter_id = 10"))
        }
    }
}

private class MergeMangaDb : AutoCloseable {

    val driver: SqlDriver = JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties().apply { put("foreign_keys", "true") },
    ).also { Database.Schema.create(it) }

    private val database = Database(
        driver = driver,
        historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
        mangasAdapter = Mangas.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
            memoAdapter = MemoColumnAdapter,
        ),
        chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
    )

    val handler = AndroidMangaDatabaseHandler(database, driver)

    init {
        insertManga(id = 1)
        insertManga(id = 2)
    }

    private fun exec(sql: String) = driver.execute(null, sql, 0)

    private fun insertManga(id: Long) = exec(
        "INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, " +
            "viewer, chapter_flags, cover_last_modified, date_added, next_update) " +
            "VALUES ($id, 1, 'url-$id', 'Title', 1, 1, 1, 0, 0, 0, 0, 0)",
    )

    fun insertCategory(id: Long) = exec(
        "INSERT INTO categories(_id, name, sort, flags, hidden) VALUES ($id, 'Category $id', $id, 0, 0)",
    )

    fun insertMangaCategory(mangaId: Long, categoryId: Long) = exec(
        "INSERT INTO mangas_categories(manga_id, category_id) VALUES ($mangaId, $categoryId)",
    )

    fun insertChapter(id: Long, mangaId: Long, url: String, bookmark: Boolean = false) = exec(
        "INSERT INTO chapters(_id, manga_id, url, name, read, bookmark, last_page_read, chapter_number, " +
            "source_order, date_fetch, date_upload) " +
            "VALUES ($id, $mangaId, '$url', 'Chapter', 0, ${if (bookmark) 1 else 0}, 0, 1, 0, 0, 0)",
    )

    fun insertHistory(chapterId: Long, lastRead: Long?, timeRead: Long) = exec(
        "INSERT INTO history(chapter_id, last_read, time_read) VALUES ($chapterId, $lastRead, $timeRead)",
    )

    fun longs(sql: String): List<Long?> = driver.executeQuery(
        null,
        sql,
        { cursor ->
            val values = mutableListOf<Long?>()
            while (cursor.next().value) values += cursor.getLong(0)
            QueryResult.Value(values)
        },
        0,
    ).value

    override fun close() = driver.close()
}
