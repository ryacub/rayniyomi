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

internal fun SqlDriver.longs(sql: String): List<Long> = strings(sql).map { it!!.toLong() }

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

    fun setFlags(mangaId: Long, viewer: Long, chapterFlags: Long, favorite: Boolean = true) = driver.exec(
        "UPDATE mangas SET viewer = $viewer, chapter_flags = $chapterFlags, favorite = ${if (favorite) 1 else 0} " +
            "WHERE _id = $mangaId",
    )

    @Suppress("LongParameterList")
    fun insertChapter(
        id: Long,
        mangaId: Long,
        url: String,
        number: Double,
        read: Boolean = false,
        bookmark: Boolean = false,
        lastPageRead: Long = 0,
    ) = driver.exec(
        "INSERT INTO chapters(_id, manga_id, url, name, read, bookmark, last_page_read, chapter_number, " +
            "source_order, date_fetch, date_upload) VALUES ($id, $mangaId, '$url', 'Chapter $number', " +
            "${if (read) 1 else 0}, ${if (bookmark) 1 else 0}, $lastPageRead, $number, 0, 0, 0)",
    )

    fun insertHistory(chapterId: Long, lastRead: Long, timeRead: Long) = driver.exec(
        "INSERT INTO history(chapter_id, last_read, time_read) VALUES ($chapterId, $lastRead, $timeRead)",
    )

    fun insertCategory(id: Long) = driver.exec(
        "INSERT INTO categories(_id, name, sort, flags) VALUES ($id, 'Category $id', $id, 0)",
    )

    fun insertMangaCategory(mangaId: Long, categoryId: Long) = driver.exec(
        "INSERT INTO mangas_categories(manga_id, category_id) VALUES ($mangaId, $categoryId)",
    )

    fun insertTrack(mangaId: Long, syncId: Long, remoteId: Long, lastRead: Double) = driver.exec(
        "INSERT INTO manga_sync(manga_id, sync_id, remote_id, title, last_chapter_read, total_chapters, " +
            "status, score, remote_url, start_date, finish_date) " +
            "VALUES ($mangaId, $syncId, $remoteId, 'Remote', $lastRead, 10, 1, 0, 'https://t/$remoteId', 0, 0)",
    )

    fun chapterValue(mangaId: Long, url: String, column: String): Long = driver.longs(
        "SELECT $column FROM chapters WHERE manga_id = $mangaId AND url = '$url'",
    ).single()

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

    fun setSeasonsFetchType(animeId: Long) = driver.exec("UPDATE animes SET fetch_type = 0 WHERE _id = $animeId")

    @Suppress("LongParameterList")
    fun insertEpisode(
        id: Long,
        animeId: Long,
        url: String,
        number: Double,
        seen: Boolean = false,
        lastSecond: Long = 0,
    ) =
        driver.exec(
            "INSERT INTO episodes(_id, anime_id, url, name, seen, bookmark, last_second_seen, total_seconds, " +
                "episode_number, source_order, date_fetch, date_upload, fillermark) VALUES ($id, $animeId, '$url', " +
                "'Episode $number', ${if (seen) 1 else 0}, 0, $lastSecond, 1440, $number, 0, 0, 0, 0)",
        )

    fun insertHistory(episodeId: Long, lastSeen: Long) = driver.exec(
        "INSERT INTO animehistory(episode_id, last_seen) VALUES ($episodeId, $lastSeen)",
    )

    fun episodeValue(animeId: Long, url: String, column: String): Long = driver.longs(
        "SELECT $column FROM episodes WHERE anime_id = $animeId AND url = '$url'",
    ).single()

    override fun close() = driver.close()
}
