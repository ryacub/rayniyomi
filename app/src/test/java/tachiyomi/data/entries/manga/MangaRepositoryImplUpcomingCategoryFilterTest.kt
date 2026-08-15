package tachiyomi.data.entries.manga

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler

class MangaRepositoryImplUpcomingCategoryFilterTest {

    private val statuses = setOf(SManga.ONGOING.toLong(), SManga.PUBLISHING_FINISHED.toLong())

    private fun repository(db: CategoryFilterUpcomingMangaDb) = MangaRepositoryImpl(db.handler)

    private fun seed(db: CategoryFilterUpcomingMangaDb) {
        db.insertManga(id = 1, title = "ActionManga")
        db.insertManga(id = 2, title = "DramaManga")
        db.insertManga(id = 3, title = "BothManga")
        db.insertManga(id = 4, title = "UncategorizedA")
        db.insertManga(id = 5, title = "UncategorizedB")
        db.insertCategory(id = 1, name = "Action")
        db.insertCategory(id = 2, name = "Drama")
        db.insertMangaCategory(rowId = 1, mangaId = 1, categoryId = 1)
        db.insertMangaCategory(rowId = 2, mangaId = 2, categoryId = 2)
        db.insertMangaCategory(rowId = 3, mangaId = 3, categoryId = 1)
        db.insertMangaCategory(rowId = 4, mangaId = 3, categoryId = 2)
    }

    private suspend fun filtered(
        db: CategoryFilterUpcomingMangaDb,
        included: List<Long>,
        excluded: List<Long>,
    ): Set<Long> {
        return repository(db)
            .getUpcomingManga(
                statuses = statuses,
                includedCategories = included,
                excludedCategories = excluded,
            )
            .first()
            .map { it.id }
            .toSet()
    }

    @Test
    fun `empty filter lists return all rows`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, emptyList(), emptyList())

            assertEquals(setOf(1L, 2L, 3L, 4L, 5L), mangaIds)
        }
    }

    @Test
    fun `include is the union of two categories`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = listOf(1L, 2L), excluded = emptyList())

            assertEquals(setOf(1L, 2L, 3L), mangaIds)
        }
    }

    @Test
    fun `default category includes only uncategorized entries`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = listOf(0L), excluded = emptyList())

            assertEquals(setOf(4L, 5L), mangaIds)
        }
    }

    @Test
    fun `include of default and a category is their union`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = listOf(0L, 1L), excluded = emptyList())

            assertEquals(setOf(1L, 3L, 4L, 5L), mangaIds)
        }
    }

    @Test
    fun `exclude hides entries in any excluded category even when also included`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = listOf(1L, 2L), excluded = listOf(2L))

            assertEquals(setOf(1L), mangaIds)
        }
    }

    @Test
    fun `exclude of default hides only uncategorized entries`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = emptyList(), excluded = listOf(0L))

            assertEquals(setOf(1L, 2L, 3L), mangaIds)
        }
    }

    @Test
    fun `exclude of a category hides only its entries`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = emptyList(), excluded = listOf(2L))

            assertEquals(setOf(1L, 4L, 5L), mangaIds)
        }
    }

    @Test
    fun `unknown included id matches nothing without crashing`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = listOf(99L), excluded = emptyList())

            assertTrue(mangaIds.isEmpty())
        }
    }

    @Test
    fun `unknown excluded id keeps all rows without crashing`() = runTest {
        CategoryFilterUpcomingMangaDb().use { db ->
            seed(db)

            val mangaIds = filtered(db, included = emptyList(), excluded = listOf(99L))

            assertEquals(setOf(1L, 2L, 3L, 4L, 5L), mangaIds)
        }
    }
}

private class CategoryFilterUpcomingMangaDb : AutoCloseable {
    private val now = System.currentTimeMillis()

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
            "viewer, chapter_flags, cover_last_modified, date_added, next_update) " +
            "VALUES ($id, 1, 'url-$id', '$title', 1, 1, 1, 0, 0, 0, 0, ${now + id * 1000})",
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
