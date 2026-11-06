package tachiyomi.data.recovery

import app.cash.sqldelight.db.SqlDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.mi.data.AnimeDatabase

class RecoverySchemaMigrationTest {

    private val recoveryObjects = "name LIKE 'recovery_%'"

    private fun SqlDriver.recoverySchema(): List<String?> =
        strings("SELECT sql FROM sqlite_master WHERE $recoveryObjects ORDER BY name")

    private fun SqlDriver.dropRecoveryObjects() {
        exec("DROP TABLE recovery_entries")
        exec("DROP TABLE recovery_sessions")
    }

    @Test
    fun `the manga migration creates the same recovery schema as a fresh install`() {
        val fresh = inMemoryDriver().also { Database.Schema.create(it) }
        val upgraded = inMemoryDriver().also { Database.Schema.create(it) }
        upgraded.dropRecoveryObjects()

        Database.Schema.migrate(upgraded, Database.Schema.version - 1, Database.Schema.version)

        val expected = fresh.recoverySchema()
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, upgraded.recoverySchema())
    }

    @Test
    fun `the anime migration creates the same recovery schema as a fresh install`() {
        val fresh = inMemoryDriver().also { AnimeDatabase.Schema.create(it) }
        val upgraded = inMemoryDriver().also { AnimeDatabase.Schema.create(it) }
        upgraded.dropRecoveryObjects()

        AnimeDatabase.Schema.migrate(upgraded, AnimeDatabase.Schema.version - 1, AnimeDatabase.Schema.version)

        val expected = fresh.recoverySchema()
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, upgraded.recoverySchema())
    }
}
