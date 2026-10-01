package tachiyomi.data.entries.manga

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.Chapters
import data.History
import data.Mangas
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate

class MemoPersistenceTest {

    private val empty = JsonObject(emptyMap())
    private val mangaMemo = JsonObject(mapOf("slug" to JsonPrimitive("series-1a2b")))
    private val chapterMemo = JsonObject(mapOf("id" to JsonPrimitive(42)))

    @Test
    fun `schema version counts the memo migration`() {
        assertEquals(38L, Database.Schema.version)
    }

    @Test
    fun `manga memo survives insert and partial updates`() = runTest {
        MemoDb().use { db ->
            val repository = MangaRepositoryImpl(db.handler)
            val id = repository.insertManga(newManga("/series/1", mangaMemo))!!

            assertEquals(mangaMemo, repository.getMangaById(id).memo)

            val newMemo = JsonObject(mapOf("slug" to JsonPrimitive("series-9z")))
            repository.updateManga(MangaUpdate(id = id, memo = newMemo))
            assertEquals(newMemo, repository.getMangaById(id).memo)

            repository.updateManga(MangaUpdate(id = id, title = "Renamed"))
            assertEquals(newMemo, repository.getMangaById(id).memo)
        }
    }

    @Test
    fun `chapter memo survives insert and partial updates`() = runTest {
        MemoDb().use { db ->
            val mangaId = MangaRepositoryImpl(db.handler).insertManga(newManga("/series/1", empty))!!
            val repository = ChapterRepositoryImpl(db.handler)
            val chapter = repository.addAllChapters(
                listOf(Chapter.create().copy(mangaId = mangaId, url = "/ch/1", name = "1", memo = chapterMemo)),
            ).single()

            assertEquals(chapterMemo, repository.getChapterById(chapter.id)!!.memo)

            val newMemo = JsonObject(mapOf("id" to JsonPrimitive(43)))
            repository.updateChapter(ChapterUpdate(id = chapter.id, memo = newMemo))
            assertEquals(newMemo, repository.getChapterById(chapter.id)!!.memo)

            repository.updateChapter(ChapterUpdate(id = chapter.id, read = true))
            assertEquals(newMemo, repository.getChapterById(chapter.id)!!.memo)
        }
    }

    @Test
    fun `migration adds memo columns that match a fresh schema`() = runTest {
        val fresh = MemoDb().use { it.tableInfo("mangas") to it.tableInfo("chapters") }

        MemoDb().use { db ->
            db.dropMemoColumns()
            db.insertRawMangaAndChapter()

            Database.Schema.migrate(db.driver, oldVersion = 37, newVersion = 38)

            assertEquals(fresh, db.tableInfo("mangas") to db.tableInfo("chapters"))
            assertEquals(empty, MangaRepositoryImpl(db.handler).getMangaById(1).memo)
            assertEquals(empty, ChapterRepositoryImpl(db.handler).getChapterById(1)!!.memo)
            assertEquals(empty, MangaRepositoryImpl(db.handler).getLibraryManga().single().manga.memo)
        }
    }

    @Test
    fun `every memo row is stored as text`() = runTest {
        // JDBC cannot reproduce the Android getBlob NUL; the storage class is the proxy (R1079).
        MemoDb().use { db ->
            db.insertRawMangaAndChapter()
            val mangas = MangaRepositoryImpl(db.handler)
            val chapters = ChapterRepositoryImpl(db.handler)
            val id = mangas.insertManga(newManga("/series/2", mangaMemo))!!
            mangas.updateManga(MangaUpdate(id = 1, memo = mangaMemo))
            chapters.addAllChapters(listOf(Chapter.create().copy(mangaId = id, url = "/ch/2", memo = chapterMemo)))
            chapters.updateChapter(ChapterUpdate(id = 1, memo = chapterMemo))

            assertEquals(listOf("text", "text"), db.memoTypes("mangas"))
            assertEquals(listOf("text", "text"), db.memoTypes("chapters"))
        }
        MemoDb().use { migrated ->
            migrated.dropMemoColumns()
            migrated.insertRawMangaAndChapter()
            Database.Schema.migrate(migrated.driver, oldVersion = 37, newVersion = 38)
            assertEquals(listOf("text"), migrated.memoTypes("mangas"))
            assertEquals(listOf("text"), migrated.memoTypes("chapters"))
        }
    }

    private fun newManga(url: String, memo: JsonObject): Manga =
        Manga.create().copy(source = 1, url = url, title = "Series", favorite = true, memo = memo)
}

private class MemoDb : AutoCloseable {
    val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { Database.Schema.create(it) }

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

    fun dropMemoColumns() {
        driver.execute(null, "ALTER TABLE mangas DROP COLUMN memo", 0)
        driver.execute(null, "ALTER TABLE chapters DROP COLUMN memo", 0)
    }

    fun insertRawMangaAndChapter() {
        driver.execute(
            null,
            "INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, " +
                "viewer, chapter_flags, cover_last_modified, date_added) " +
                "VALUES (1, 1, '/series/1', 'Series', 0, 1, 1, 0, 0, 0, 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO chapters(_id, manga_id, url, name, read, bookmark, last_page_read, " +
                "chapter_number, source_order, date_fetch, date_upload) " +
                "VALUES (1, 1, '/ch/1', '1', 0, 0, 0, 1.0, 0, 0, 0)",
            0,
        )
    }

    fun memoTypes(table: String): List<String> {
        val rows = mutableListOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT typeof(memo) FROM $table",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) rows += cursor.getString(0).toString()
                QueryResult.Unit
            },
        )
        return rows
    }

    fun tableInfo(table: String): List<String> {
        val rows = mutableListOf<String>()
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA table_info($table)",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    rows += listOf(1, 2, 3, 4).joinToString(" ") { cursor.getString(it).toString() }
                }
                QueryResult.Unit
            },
        )
        return rows
    }

    override fun close() = driver.close()
}
