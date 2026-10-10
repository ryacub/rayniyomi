package tachiyomi.data.entries.anime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dataanime.Animehistory
import dataanime.Animes
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase
import java.util.Properties

class AnimeRepositoryImplMergeTest {

    private val keepId = 1L
    private val deleteId = 2L

    private suspend fun MergeAnimeDb.merge() = AnimeRepositoryImpl(handler).mergeEntries(keepId, deleteId)

    @Test
    fun `a category on both entries leaves one category row`() = runTest {
        MergeAnimeDb().use { db ->
            db.insertCategory(id = 1)
            db.insertAnimeCategory(animeId = keepId, categoryId = 1)
            db.insertAnimeCategory(animeId = deleteId, categoryId = 1)

            db.merge()

            assertEquals(listOf(1L), db.longs("SELECT category_id FROM animes_categories WHERE anime_id = $keepId"))
        }
    }

    @Test
    fun `bookmark and fillermark on the deleted entry survive on the shared episode`() = runTest {
        MergeAnimeDb().use { db ->
            db.insertEpisode(id = 10, animeId = keepId, url = "e1", marked = false)
            db.insertEpisode(id = 20, animeId = deleteId, url = "e1", marked = true)

            db.merge()

            assertEquals(listOf(1L), db.longs("SELECT bookmark FROM episodes WHERE _id = 10"))
            assertEquals(listOf(1L), db.longs("SELECT fillermark FROM episodes WHERE _id = 10"))
        }
    }

    @Test
    fun `history on both shared episodes keeps the latest watch`() = runTest {
        MergeAnimeDb().use { db ->
            db.insertEpisode(id = 10, animeId = keepId, url = "e1")
            db.insertEpisode(id = 20, animeId = deleteId, url = "e1")
            db.insertHistory(episodeId = 10, lastSeen = 1_000)
            db.insertHistory(episodeId = 20, lastSeen = 2_000)

            db.merge()

            assertEquals(listOf(2_000L), db.longs("SELECT last_seen FROM animehistory WHERE episode_id = 10"))
        }
    }

    @Test
    fun `history of the deleted entry moves to the shared episode`() = runTest {
        MergeAnimeDb().use { db ->
            db.insertEpisode(id = 10, animeId = keepId, url = "e1")
            db.insertEpisode(id = 20, animeId = deleteId, url = "e1")
            db.insertHistory(episodeId = 20, lastSeen = 2_000)

            db.merge()

            assertEquals(listOf(2_000L), db.longs("SELECT last_seen FROM animehistory WHERE episode_id = 10"))
        }
    }
}

private class MergeAnimeDb : AutoCloseable {

    val driver: SqlDriver = JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties().apply { put("foreign_keys", "true") },
    ).also { AnimeDatabase.Schema.create(it) }

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

    init {
        insertAnime(id = 1)
        insertAnime(id = 2)
    }

    private fun exec(sql: String) = driver.execute(null, sql, 0)

    private fun insertAnime(id: Long) = exec(
        "INSERT INTO animes(_id, source, url, title, status, favorite, initialized, viewer, " +
            "episode_flags, cover_last_modified, date_added, season_flags, season_number, " +
            "season_source_order, background_last_modified, next_update) " +
            "VALUES ($id, 1, 'url-$id', 'Title', 1, 1, 1, 0, 0, 0, 0, 0, 1.0, 0, 0, 0)",
    )

    fun insertCategory(id: Long) = exec(
        "INSERT INTO categories(_id, name, sort, flags, hidden) VALUES ($id, 'Category $id', $id, 0, 0)",
    )

    fun insertAnimeCategory(animeId: Long, categoryId: Long) = exec(
        "INSERT INTO animes_categories(anime_id, category_id) VALUES ($animeId, $categoryId)",
    )

    fun insertEpisode(id: Long, animeId: Long, url: String, marked: Boolean = false) {
        val flag = if (marked) 1 else 0
        exec(
            "INSERT INTO episodes(_id, anime_id, url, name, seen, bookmark, fillermark, last_second_seen, " +
                "total_seconds, episode_number, source_order, date_fetch, date_upload) " +
                "VALUES ($id, $animeId, '$url', 'Episode', 0, $flag, $flag, 0, 0, 1, 0, 0, 0)",
        )
    }

    fun insertHistory(episodeId: Long, lastSeen: Long) = exec(
        "INSERT INTO animehistory(episode_id, last_seen) VALUES ($episodeId, $lastSeen)",
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
