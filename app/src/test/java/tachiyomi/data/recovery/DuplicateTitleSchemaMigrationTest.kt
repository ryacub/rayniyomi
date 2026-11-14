package tachiyomi.data.recovery

import app.cash.sqldelight.db.SqlDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.mi.data.AnimeDatabase

class DuplicateTitleSchemaMigrationTest {

    @Test
    fun `manga upgrade removes the duplicate key and preserves library data`() {
        MangaRecoveryDb().use { db ->
            db.insertManga(1)
            db.insertChapter(2, 1, "chapter", 1.0, read = true)
            db.insertCategory(1)
            db.insertMangaCategory(1, 1)
            db.driver.installLegacyDuplicateKey("mangas")

            Database.Schema.migrate(db.driver, 41, Database.Schema.version)

            assertEquals(emptyList<String>(), db.driver.duplicateKeys("mangas"))
            assertEquals(listOf(1L), db.driver.longs("SELECT _id FROM mangas WHERE favorite = 1"))
            assertEquals(listOf(2L), db.driver.longs("SELECT _id FROM chapters WHERE manga_id = 1 AND read = 1"))
            assertEquals(listOf(1L), db.driver.longs("SELECT category_id FROM mangas_categories WHERE manga_id = 1"))
            assertEquals(emptyList<String>(), db.driver.strings("PRAGMA foreign_key_check"))
        }
    }

    @Test
    fun `anime upgrade removes the duplicate key and preserves library data`() {
        AnimeRecoveryDb().use { db ->
            db.insertAnime(1)
            db.insertEpisode(2, 1, "episode", 1.0, seen = true)
            db.driver.installLegacyDuplicateKey("animes")

            AnimeDatabase.Schema.migrate(db.driver, 144, AnimeDatabase.Schema.version)

            assertEquals(emptyList<String>(), db.driver.duplicateKeys("animes"))
            assertEquals(listOf(1L), db.driver.longs("SELECT _id FROM animes WHERE favorite = 1"))
            assertEquals(listOf(2L), db.driver.longs("SELECT _id FROM episodes WHERE anime_id = 1 AND seen = 1"))
            assertEquals(emptyList<String>(), db.driver.strings("PRAGMA foreign_key_check"))
        }
    }

    private fun SqlDriver.installLegacyDuplicateKey(table: String) {
        val column = strings("SELECT name FROM pragma_table_info('$table') WHERE name = 'normalized_title'")
        if (column.isEmpty()) exec("ALTER TABLE $table ADD COLUMN normalized_title TEXT NOT NULL DEFAULT ''")
        exec("CREATE INDEX ${table}_normalized_title_idx ON $table(normalized_title)")
    }

    private fun SqlDriver.duplicateKeys(table: String): List<String?> = strings(
        """
        SELECT name FROM pragma_table_info('$table') WHERE name = 'normalized_title'
        UNION ALL
        SELECT name FROM pragma_index_list('$table') WHERE name = '${table}_normalized_title_idx'
        """.trimIndent(),
    )
}
