package eu.kanade.tachiyomi.ui.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.Charsets.UTF_8

class MainActivityStartupMigrationTest {

    @Test
    fun `startup composition is not gated on migration completion`() {
        val source = loadMainActivitySource()

        assertFalse(
            source.contains("migrationChecked"),
            "MainActivity must not keep composition behind a migrationChecked state gate",
        )
        assertFalse(
            source.contains("return@setComposeContent"),
            "MainActivity must not return from setComposeContent while waiting on migration",
        )
    }

    @Test
    fun `successful migration shows changelog in release builds`() {
        assertTrue(shouldShowPostMigrationChangelog(didMigration = true, isDebugBuild = false))
    }

    @Test
    fun `successful migration does not show changelog in debug builds`() {
        assertFalse(shouldShowPostMigrationChangelog(didMigration = true, isDebugBuild = true))
    }

    @Test
    fun `failed or noop migration does not show changelog`() {
        assertFalse(shouldShowPostMigrationChangelog(didMigration = false, isDebugBuild = false))
        assertFalse(shouldShowPostMigrationChangelog(didMigration = false, isDebugBuild = true))
    }

    private fun loadMainActivitySource(): String {
        val moduleRelative = Paths.get(
            "src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt",
        )
        val rootRelative = Paths.get(
            "app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt",
        )
        val sourcePath = when {
            Files.exists(moduleRelative) -> moduleRelative
            Files.exists(rootRelative) -> rootRelative
            else -> throw java.nio.file.NoSuchFileException(
                "Could not find MainActivity.kt from module or repo root paths",
            )
        }
        return String(Files.readAllBytes(sourcePath), UTF_8)
    }
}
