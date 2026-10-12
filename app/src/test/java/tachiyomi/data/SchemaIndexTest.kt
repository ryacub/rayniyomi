package tachiyomi.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase

class SchemaIndexTest {

    private val redundantMangaIndexes = setOf(
        "history_history_chapter_id_index",
        "excluded_scanlators_manga_id_index",
        "mangas_normalized_title_idx",
    )

    private val redundantAnimeIndexes = setOf(
        "animehistory_history_episode_id_index",
        "animes_normalized_title_idx",
    )

    @Test
    fun `a fresh manga schema has no redundant indexes`() {
        InMemoryMangaDb().use { db ->
            assertNoneOf(redundantMangaIndexes, db.driver.indexNames())
        }
    }

    @Test
    fun `a fresh anime schema has no redundant indexes`() {
        InMemoryAnimeDb().use { db ->
            assertNoneOf(redundantAnimeIndexes, db.driver.indexNames())
        }
    }

    @Test
    fun `the manga migration drops redundant indexes and keeps the scanlator lookup index`() {
        InMemoryMangaDb().use { db ->
            db.driver.exec("DROP INDEX excluded_scanlators_manga_id_scanlator_index")
            db.driver.exec(
                "CREATE INDEX IF NOT EXISTS history_history_chapter_id_index ON history(chapter_id)",
            )
            db.driver.exec(
                "CREATE INDEX IF NOT EXISTS excluded_scanlators_manga_id_index ON excluded_scanlators(manga_id)",
            )
            db.driver.exec("CREATE INDEX IF NOT EXISTS mangas_normalized_title_idx ON mangas(normalized_title)")

            Database.Schema.migrate(db.driver, oldVersion = 38, newVersion = Database.Schema.version)

            val indexes = db.driver.indexNames()
            assertNoneOf(redundantMangaIndexes, indexes)
            assertTrue("excluded_scanlators_manga_id_scanlator_index" in indexes, indexes.toString())
        }
    }

    @Test
    fun `the anime migration drops redundant indexes`() {
        InMemoryAnimeDb().use { db ->
            db.driver.exec(
                "CREATE INDEX IF NOT EXISTS animehistory_history_episode_id_index ON animehistory(episode_id)",
            )
            db.driver.exec("CREATE INDEX IF NOT EXISTS animes_normalized_title_idx ON animes(normalized_title)")

            AnimeDatabase.Schema.migrate(db.driver, oldVersion = 140, newVersion = AnimeDatabase.Schema.version)

            assertNoneOf(redundantAnimeIndexes, db.driver.indexNames())
        }
    }

    private fun assertNoneOf(expectedAbsent: Set<String>, indexes: Set<String>) {
        val present = expectedAbsent intersect indexes
        assertTrue(present.isEmpty(), "redundant indexes present: $present")
    }

    private fun SqlDriver.exec(sql: String) = execute(null, sql, 0)

    private fun SqlDriver.indexNames(): Set<String> {
        val names = mutableSetOf<String>()
        executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'index'",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    names += cursor.getString(0)!!
                }
                QueryResult.Unit
            },
        )
        return names
    }
}
