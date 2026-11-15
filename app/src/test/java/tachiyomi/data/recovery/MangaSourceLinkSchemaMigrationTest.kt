package tachiyomi.data.recovery

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database

class MangaSourceLinkSchemaMigrationTest {

    @Test
    fun `the manga migration creates the source link table from schema version 42`() {
        val driver = inMemoryDriver().also { Database.Schema.create(it) }
        try {
            driver.exec("DROP TABLE IF EXISTS manga_source_links")

            Database.Schema.migrate(driver, oldVersion = 42, newVersion = Database.Schema.version)

            assertTrue(
                driver.strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'manga_source_links'").isNotEmpty(),
                "the migration must create the manga source link table",
            )
        } finally {
            driver.close()
        }
    }
}
