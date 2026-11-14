package tachiyomi.data.recovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.mi.data.AnimeDatabase

class AnimeExtensionLibMigrationTest {

    @Test
    fun `upgrading the anime database adds extension metadata and relations without losing entries`() {
        AnimeRecoveryDb().use { fixture ->
            val driver = fixture.driver
            fixture.insertAnime(1)
            fixture.insertEpisode(2, 1, "episode", 1.0, seen = true, lastSecond = 123)
            driver.exec("DROP TABLE IF EXISTS anime_relations")
            for (table in listOf("animes", "episodes")) {
                val hasMemo = driver.strings("SELECT name FROM pragma_table_info('$table')").contains("memo")
                if (hasMemo) driver.exec("ALTER TABLE $table DROP COLUMN memo")
            }

            AnimeDatabase.Schema.migrate(driver, 142, 144)

            assertEquals(
                listOf("anime_relations", "animes.memo", "episodes.memo"),
                driver.strings(
                    """
                    SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'anime_relations'
                    UNION ALL SELECT 'animes.memo' FROM pragma_table_info('animes') WHERE name = 'memo'
                    UNION ALL SELECT 'episodes.memo' FROM pragma_table_info('episodes') WHERE name = 'memo'
                    """.trimIndent(),
                ),
            )
            assertEquals(listOf("Title 1"), driver.strings("SELECT title FROM animes WHERE _id = 1"))
            assertEquals(listOf(123L), driver.longs("SELECT last_second_seen FROM episodes WHERE _id = 2 AND seen = 1"))
        }
    }
}
