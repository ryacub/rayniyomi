package eu.kanade.tachiyomi.data.backup.restore.restorers

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase

/**
 * An in-memory SQLDelight database for restore tests.
 *
 * The production handlers accept any [SqlDriver], so the JDBC driver gives the real
 * query path on the JVM without an Android device.
 */
internal class InMemoryMangaDb : AutoCloseable {
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

    fun insertManga(id: Long, url: String, title: String) = driver.execute(
        null,
        "INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, " +
            "viewer, chapter_flags, cover_last_modified, date_added) " +
            "VALUES ($id, 1, '$url', '$title', 0, 1, 1, 0, 0, 0, 0)",
        0,
    )

    fun insertChapter(id: Long, mangaId: Long, url: String) = driver.execute(
        null,
        "INSERT INTO chapters(_id, manga_id, url, name, read, bookmark, last_page_read, " +
            "chapter_number, source_order, date_fetch, date_upload) " +
            "VALUES ($id, $mangaId, '$url', 'chapter', 0, 0, 0, 1.0, 0, 0, 0)",
        0,
    )

    fun insertHistory(id: Long, chapterId: Long, lastRead: Long, timeRead: Long = 0) = driver.execute(
        null,
        "INSERT INTO history(_id, chapter_id, last_read, time_read) " +
            "VALUES ($id, $chapterId, $lastRead, $timeRead)",
        0,
    )

    fun historyRows(): List<Triple<Long, Long, Long>> {
        val rows = mutableListOf<Triple<Long, Long, Long>>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT _id, chapter_id, IFNULL(last_read, 0) FROM history ORDER BY _id",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    rows += Triple(cursor.getLong(0)!!, cursor.getLong(1)!!, cursor.getLong(2)!!)
                }
                QueryResult.Unit
            },
        )
        return rows
    }

    override fun close() = driver.close()
}

internal class InMemoryAnimeDb : AutoCloseable {
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

    fun insertAnime(id: Long, url: String, title: String) = driver.execute(
        null,
        "INSERT INTO animes(_id, source, url, title, status, favorite, initialized, viewer, " +
            "episode_flags, cover_last_modified, date_added, season_flags, season_number, " +
            "season_source_order, background_last_modified) " +
            "VALUES ($id, 1, '$url', '$title', 0, 1, 1, 0, 0, 0, 0, 0, 1.0, 0, 0)",
        0,
    )

    fun insertEpisode(id: Long, animeId: Long, url: String) = driver.execute(
        null,
        "INSERT INTO episodes(_id, anime_id, url, name, seen, bookmark, last_second_seen, " +
            "total_seconds, episode_number, source_order, date_fetch, date_upload, fillermark) " +
            "VALUES ($id, $animeId, '$url', 'episode', 0, 0, 0, 0, 1.0, 0, 0, 0, 0)",
        0,
    )

    fun insertHistory(id: Long, episodeId: Long, lastSeen: Long) = driver.execute(
        null,
        "INSERT INTO animehistory(_id, episode_id, last_seen) VALUES ($id, $episodeId, $lastSeen)",
        0,
    )

    fun historyRows(): List<Triple<Long, Long, Long>> {
        val rows = mutableListOf<Triple<Long, Long, Long>>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT _id, episode_id, IFNULL(last_seen, 0) FROM animehistory ORDER BY _id",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    rows += Triple(cursor.getLong(0)!!, cursor.getLong(1)!!, cursor.getLong(2)!!)
                }
                QueryResult.Unit
            },
        )
        return rows
    }

    override fun close() = driver.close()
}
