package tachiyomi.data.entries.anime

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dataanime.Animehistory
import dataanime.Animes
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase

class AnimeRepositoryImplUpcomingCategoryFilterTest {

    private val statuses = setOf(SAnime.ONGOING.toLong(), SAnime.PUBLISHING_FINISHED.toLong())

    private fun repository(db: CategoryFilterUpcomingAnimeDb) = AnimeRepositoryImpl(db.handler)

    private fun seed(db: CategoryFilterUpcomingAnimeDb) {
        db.insertAnime(id = 1, title = "ActionAnime")
        db.insertAnime(id = 2, title = "DramaAnime")
        db.insertAnime(id = 3, title = "BothAnime")
        db.insertAnime(id = 4, title = "UncategorizedA")
        db.insertAnime(id = 5, title = "UncategorizedB")
        db.insertCategory(id = 1, name = "Action")
        db.insertCategory(id = 2, name = "Drama")
        db.insertAnimeCategory(rowId = 1, animeId = 1, categoryId = 1)
        db.insertAnimeCategory(rowId = 2, animeId = 2, categoryId = 2)
        db.insertAnimeCategory(rowId = 3, animeId = 3, categoryId = 1)
        db.insertAnimeCategory(rowId = 4, animeId = 3, categoryId = 2)
    }

    private suspend fun filtered(
        db: CategoryFilterUpcomingAnimeDb,
        included: List<Long>,
        excluded: List<Long>,
    ): Set<Long> {
        return repository(db)
            .getUpcomingAnime(
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
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, emptyList(), emptyList())

            assertEquals(setOf(1L, 2L, 3L, 4L, 5L), animeIds)
        }
    }

    @Test
    fun `include is the union of two categories`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = listOf(1L, 2L), excluded = emptyList())

            assertEquals(setOf(1L, 2L, 3L), animeIds)
        }
    }

    @Test
    fun `default category includes only uncategorized entries`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = listOf(0L), excluded = emptyList())

            assertEquals(setOf(4L, 5L), animeIds)
        }
    }

    @Test
    fun `include of default and a category is their union`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = listOf(0L, 1L), excluded = emptyList())

            assertEquals(setOf(1L, 3L, 4L, 5L), animeIds)
        }
    }

    @Test
    fun `exclude hides entries in any excluded category even when also included`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = listOf(1L, 2L), excluded = listOf(2L))

            assertEquals(setOf(1L), animeIds)
        }
    }

    @Test
    fun `exclude of default hides only uncategorized entries`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = emptyList(), excluded = listOf(0L))

            assertEquals(setOf(1L, 2L, 3L), animeIds)
        }
    }

    @Test
    fun `exclude of a category hides only its entries`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = emptyList(), excluded = listOf(2L))

            assertEquals(setOf(1L, 4L, 5L), animeIds)
        }
    }

    @Test
    fun `unknown included id matches nothing without crashing`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = listOf(99L), excluded = emptyList())

            assertTrue(animeIds.isEmpty())
        }
    }

    @Test
    fun `unknown excluded id keeps all rows without crashing`() = runTest {
        CategoryFilterUpcomingAnimeDb().use { db ->
            seed(db)

            val animeIds = filtered(db, included = emptyList(), excluded = listOf(99L))

            assertEquals(setOf(1L, 2L, 3L, 4L, 5L), animeIds)
        }
    }
}

private class CategoryFilterUpcomingAnimeDb : AutoCloseable {
    private val now = System.currentTimeMillis()

    val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { AnimeDatabase.Schema.create(it) }

    private val database = AnimeDatabase(
        driver = driver,
        animehistoryAdapter = Animehistory.Adapter(last_seenAdapter = DateColumnAdapter),
        animesAdapter = Animes.Adapter(
            genreAdapter = StringListColumnAdapter,
            update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
            fetch_typeAdapter = FetchTypeColumnAdapter,
        ),
    )

    val handler = AndroidAnimeDatabaseHandler(database, driver)

    fun insertAnime(id: Long, title: String) = driver.execute(
        null,
        "INSERT INTO animes(_id, source, url, title, status, favorite, initialized, viewer, " +
            "episode_flags, cover_last_modified, date_added, season_flags, season_number, " +
            "season_source_order, background_last_modified, next_update) " +
            "VALUES ($id, 1, 'url-$id', '$title', 1, 1, 1, 0, 0, 0, 0, 0, 1.0, 0, 0, ${now + id * 1000})",
        0,
    )

    fun insertCategory(id: Long, name: String) = driver.execute(
        null,
        "INSERT INTO categories(_id, name, sort, flags, hidden) VALUES ($id, '$name', $id, 0, 0)",
        0,
    )

    fun insertAnimeCategory(rowId: Long, animeId: Long, categoryId: Long) = driver.execute(
        null,
        "INSERT INTO animes_categories(_id, anime_id, category_id) " +
            "VALUES ($rowId, $animeId, $categoryId)",
        0,
    )

    override fun close() = driver.close()
}
