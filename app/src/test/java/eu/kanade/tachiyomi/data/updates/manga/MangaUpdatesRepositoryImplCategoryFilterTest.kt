package eu.kanade.tachiyomi.data.updates.manga

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl

class MangaUpdatesRepositoryImplCategoryFilterTest {

    private fun repository(db: CategoryFilterMangaDb) = MangaUpdatesRepositoryImpl(db.handler)

    private fun seed(db: CategoryFilterMangaDb) {
        db.insertManga(id = 1, title = "ActionManga")
        db.insertManga(id = 2, title = "DramaManga")
        db.insertManga(id = 3, title = "BothManga")
        db.insertManga(id = 4, title = "UncategorizedA")
        db.insertManga(id = 5, title = "UncategorizedB")
        db.insertChapter(id = 10, mangaId = 1)
        db.insertChapter(id = 20, mangaId = 2)
        db.insertChapter(id = 30, mangaId = 3)
        db.insertChapter(id = 40, mangaId = 4)
        db.insertChapter(id = 50, mangaId = 5)
        db.insertCategory(id = 1, name = "Action")
        db.insertCategory(id = 2, name = "Drama")
        db.insertMangaCategory(rowId = 1, mangaId = 1, categoryId = 1)
        db.insertMangaCategory(rowId = 2, mangaId = 2, categoryId = 2)
        db.insertMangaCategory(rowId = 3, mangaId = 3, categoryId = 1)
        db.insertMangaCategory(rowId = 4, mangaId = 3, categoryId = 2)
    }

    private suspend fun filtered(
        db: CategoryFilterMangaDb,
        included: List<Long>,
        excluded: List<Long>,
    ): Set<Long> {
        return repository(db)
            .subscribeAllMangaUpdatesWithCategoryFilter(
                after = 0L,
                limit = 500L,
                includedCategories = included,
                excludedCategories = excluded,
            )
            .first()
            .map { it.chapterId }
            .toSet()
    }

    @Test
    fun `empty filter lists return all rows`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, emptyList(), emptyList())

            assertEquals(setOf(10L, 20L, 30L, 40L, 50L), chapterIds)
        }
    }

    @Test
    fun `include is the union of two categories`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = listOf(1L, 2L), excluded = emptyList())

            assertEquals(setOf(10L, 20L, 30L), chapterIds)
        }
    }

    @Test
    fun `default category includes only uncategorized entries`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = listOf(0L), excluded = emptyList())

            assertEquals(setOf(40L, 50L), chapterIds)
        }
    }

    @Test
    fun `include of default and a category is their union`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = listOf(0L, 1L), excluded = emptyList())

            assertEquals(setOf(10L, 30L, 40L, 50L), chapterIds)
        }
    }

    @Test
    fun `exclude hides entries in any excluded category even when also included`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = listOf(1L, 2L), excluded = listOf(2L))

            assertEquals(setOf(10L), chapterIds)
        }
    }

    @Test
    fun `exclude of default hides only uncategorized entries`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = emptyList(), excluded = listOf(0L))

            assertEquals(setOf(10L, 20L, 30L), chapterIds)
        }
    }

    @Test
    fun `exclude of a category hides only its entries`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = emptyList(), excluded = listOf(2L))

            assertEquals(setOf(10L, 40L, 50L), chapterIds)
        }
    }

    @Test
    fun `unknown included id matches nothing without crashing`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = listOf(99L), excluded = emptyList())

            assertTrue(chapterIds.isEmpty())
        }
    }

    @Test
    fun `unknown excluded id keeps all rows without crashing`() = runTest {
        CategoryFilterMangaDb().use { db ->
            seed(db)

            val chapterIds = filtered(db, included = emptyList(), excluded = listOf(99L))

            assertEquals(setOf(10L, 20L, 30L, 40L, 50L), chapterIds)
        }
    }
}

private class CategoryFilterMangaDb : AutoCloseable {
    val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { Database.Schema.create(it) }

    private val database = Database(
        driver = driver,
        historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
        mangasAdapter = Mangas.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
        ),
    )

    val handler = AndroidMangaDatabaseHandler(database, driver)

    fun insertManga(id: Long, title: String) = driver.execute(
        null,
        "INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, " +
            "viewer, chapter_flags, cover_last_modified, date_added) " +
            "VALUES ($id, 1, 'url-$id', '$title', 0, 1, 1, 0, 0, 0, 0)",
        0,
    )

    fun insertChapter(id: Long, mangaId: Long) = driver.execute(
        null,
        "INSERT INTO chapters(_id, manga_id, url, name, read, bookmark, last_page_read, " +
            "chapter_number, source_order, date_fetch, date_upload) " +
            "VALUES ($id, $mangaId, 'url-$id', 'chapter', 0, 0, 0, 1.0, 0, 1000, 1000)",
        0,
    )

    fun insertCategory(id: Long, name: String) = driver.execute(
        null,
        "INSERT INTO categories(_id, name, sort, flags, hidden) VALUES ($id, '$name', $id, 0, 0)",
        0,
    )

    fun insertMangaCategory(rowId: Long, mangaId: Long, categoryId: Long) = driver.execute(
        null,
        "INSERT INTO mangas_categories(_id, manga_id, category_id) " +
            "VALUES ($rowId, $mangaId, $categoryId)",
        0,
    )

    override fun close() = driver.close()
}
