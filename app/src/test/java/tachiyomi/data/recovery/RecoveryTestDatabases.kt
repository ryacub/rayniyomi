package tachiyomi.data.recovery

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import data.Chapters
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase
import java.util.Properties

internal fun inMemoryDriver(): SqlDriver = JdbcSqliteDriver(
    JdbcSqliteDriver.IN_MEMORY,
    Properties().apply { put("foreign_keys", "true") },
)

internal fun SqlDriver.exec(sql: String) {
    execute(null, sql, 0)
}

internal fun SqlDriver.strings(sql: String): List<String?> = executeQuery(
    identifier = null,
    sql = sql,
    mapper = { cursor ->
        val rows = mutableListOf<String?>()
        while (cursor.next().value) rows += cursor.getString(0)
        QueryResult.Value(rows)
    },
    parameters = 0,
).value

internal class MangaRecoveryDb : AutoCloseable {

    val driver: SqlDriver = inMemoryDriver().also { Database.Schema.create(it) }

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

    fun insertManga(id: Long, source: Long = 1, url: String = "url-$id") = driver.exec(
        "INSERT INTO mangas(_id, source, url, title, status, favorite, initialized, " +
            "viewer, chapter_flags, cover_last_modified, date_added, next_update) " +
            "VALUES ($id, $source, '$url', 'Title $id', 1, 1, 1, 0, 0, 0, 0, 0)",
    )

    override fun close() = driver.close()
}

internal class AnimeRecoveryDb : AutoCloseable {

    val driver: SqlDriver = inMemoryDriver().also { AnimeDatabase.Schema.create(it) }

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

    fun insertAnime(id: Long, source: Long = 1, url: String = "url-$id") = driver.exec(
        "INSERT INTO animes(_id, source, url, title, status, favorite, initialized, viewer, " +
            "episode_flags, cover_last_modified, date_added, season_flags, season_number, " +
            "season_source_order, background_last_modified, next_update) " +
            "VALUES ($id, $source, '$url', 'Title $id', 1, 1, 1, 0, 0, 0, 0, 0, 1.0, 0, 0, 0)",
    )

    override fun close() = driver.close()
}
