package eu.kanade.tachiyomi.ui.lifecycle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CollectAsStateUsageGuardrailTest {

    private val projectRoot: Path = findProjectRoot()
    private val sourceRoot: Path = projectRoot.resolve("app/src/main/java")

    @Test
    fun `scoped ui files do not use raw collectAsState`() {
        val violating = scopedTargetFiles()
            .filter { path -> RAW_COLLECT_AS_STATE.containsMatchIn(readNormalizedCode(path)) }
            .map(::relativePath)

        assertTrue(
            violating.isEmpty(),
            "Raw collectAsState() found in scoped UI files:\n${violating.joinToString("\n")}",
        )
    }

    @Test
    fun `scoped ui file selection remains non-empty`() {
        val scopedTargets = scopedTargetFiles()
        assertTrue(
            scopedTargets.isNotEmpty(),
            "Expected scoped target set to be non-empty.",
        )
    }

    @Test
    fun `initial and initialValue defaults stay preserved on migrated screens`() {
        assertFileContains(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt",
            Regex("""getCategories\.subscribe\(\)\.collectAsStateWithLifecycle\(initialValue\s*=\s*emptyList\(\)\)"""),
        )
        assertFileContains(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt",
            Regex(
                """getAnimeCategories\.subscribe\(\)\.collectAsStateWithLifecycle\(initialValue\s*=\s*emptyList\(\)\)""",
            ),
        )
        assertFileContains(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt",
            Regex(
                """getMangaCategories\.subscribe\(\)\.collectAsStateWithLifecycle\(initialValue\s*=\s*emptyList\(\)\)""",
            ),
        )
        assertFileContains(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt",
            Regex(
                """getAnimeCategories\.subscribe\(\)\.collectAsStateWithLifecycle\(initialValue\s*=\s*emptyList\(\)\)""",
            ),
        )
        assertFileContains(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt",
            Regex("""collectAsStateWithLifecycle\(initialValue\s*=\s*trackerSyncEnabled\.get\(\)\)"""),
        )
    }

    private fun assertFileContains(relativePath: String, regex: Regex) {
        val content = projectRoot.resolve(relativePath).toFile().readText()
        assertTrue(
            regex.containsMatchIn(content),
            "Expected `${regex.pattern}` in $relativePath",
        )
    }

    private fun scopedTargetFiles(): List<Path> {
        return mainKotlinFiles()
            .filter(::isScopedTargetFile)
    }

    private fun mainKotlinFiles(): List<Path> {
        val files = mutableListOf<Path>()
        Files.walk(sourceRoot).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.toString().endsWith(".kt") }
                .forEach(files::add)
        }
        return files
    }

    private fun isScopedTargetFile(path: Path): Boolean {
        if (EXPLICIT_SCOPED_TARGETS.contains(relativePath(path))) {
            return true
        }
        val fileName = path.fileName.toString()
        return SCOPED_SUFFIXES.any(fileName::endsWith)
    }

    private fun readNormalizedCode(path: Path): String {
        return path.toFile().readText()
            .replace(TRIPLE_QUOTED_STRING, "\"\"")
            .replace(DOUBLE_QUOTED_STRING, "\"\"")
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, " ")
    }

    private fun relativePath(path: Path): String = projectRoot.relativize(path).toString().replace('\\', '/')

    private fun findProjectRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (true) {
            val sourceDir = current.resolve("app/src/main/java")
            if (Files.isDirectory(sourceDir)) {
                return current
            }
            current = current.parent ?: error("Could not locate project root from ${System.getProperty("user.dir")}")
        }
    }

    private companion object {
        val SCOPED_SUFFIXES = listOf("Screen.kt", "Tab.kt", "Dialog.kt")
        val RAW_COLLECT_AS_STATE = Regex("""(?<!WithLifecycle)\bcollectAsState\s*\(""")
        val LINE_COMMENT = Regex("""(?m)//.*$""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val DOUBLE_QUOTED_STRING = Regex(""""(?:\\.|[^"\\])*"""")
        val TRIPLE_QUOTED_STRING = Regex(""""\"\"\"[\s\S]*?\"\"\""""")
        val EXPLICIT_SCOPED_TARGETS = setOf(
            "app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt",
        )
    }
}
