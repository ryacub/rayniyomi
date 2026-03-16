package eu.kanade.tachiyomi.ui.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CollectAsStateUsageGuardrailTest {

    private val projectRoot: Path = findProjectRoot()
    private val sourceRoot: Path = projectRoot.resolve("app/src/main/java")

    @Test
    fun `guardrail target files do not use raw collectAsState`() {
        val violating = EXPECTED_TARGET_FILES
            .map(projectRoot::resolve)
            .filter { path -> RAW_COLLECT_AS_STATE.containsMatchIn(readNormalizedCode(path)) }
            .map(::relativePath)

        assertTrue(
            violating.isEmpty(),
            "Raw collectAsState() found in R451 bounded target files:\n${violating.joinToString("\n")}",
        )
    }

    @Test
    fun `guardrail target selection remains explicitly bounded`() {
        val detectedTargets = detectedScopedLifecycleFiles()
        val missingTargets = EXPECTED_TARGET_FILES - detectedTargets
        val unexpectedTargets = detectedTargets - EXPECTED_TARGET_FILES

        assertTrue(
            missingTargets.isEmpty() && unexpectedTargets.isEmpty(),
            buildString {
                appendLine("R451 bounded target set drift detected.")
                if (missingTargets.isNotEmpty()) {
                    appendLine("Missing expected targets:")
                    appendLine(missingTargets.sorted().joinToString("\n"))
                }
                if (unexpectedTargets.isNotEmpty()) {
                    appendLine("Unexpected new targets:")
                    appendLine(unexpectedTargets.sorted().joinToString("\n"))
                }
            }.trim(),
        )

        assertEquals(EXPECTED_TARGET_FILES.size, detectedTargets.size, "Target count mismatch.")
        assertTrue(EXPECTED_TARGET_FILES.isNotEmpty(), "Expected bounded target set to be non-empty.")
        assertTrue(
            EXPECTED_TARGET_FILES.all { relative ->
                val path = projectRoot.resolve(relative)
                Files.isRegularFile(path) && isScopedTargetFile(path)
            },
            "Expected bounded targets include non-scoped or missing files.",
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

    private fun detectedScopedLifecycleFiles(): Set<String> {
        return mainKotlinFiles()
            .filter(::isScopedTargetFile)
            .filter { path -> COLLECT_AS_STATE_WITH_LIFECYCLE.containsMatchIn(readNormalizedCode(path)) }
            .map(::relativePath)
            .toSet()
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
        val COLLECT_AS_STATE_WITH_LIFECYCLE = Regex("""\bcollectAsStateWithLifecycle\s*\(""")
        val LINE_COMMENT = Regex("""(?m)//.*$""")
        val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        val DOUBLE_QUOTED_STRING = Regex(""""(?:\\.|[^"\\])*"""")
        val TRIPLE_QUOTED_STRING = Regex(""""\"\"\"[\s\S]*?\"\"\""""")
        val EXPECTED_TARGET_FILES = setOf(
            "app/src/main/java/eu/kanade/presentation/entries/anime/AnimeScreen.kt",
            "app/src/main/java/eu/kanade/presentation/entries/anime/EpisodeOptionsDialogScreen.kt",
            "app/src/main/java/eu/kanade/presentation/entries/manga/MangaScreen.kt",
            "app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibrarySettingsDialog.kt",
            "app/src/main/java/eu/kanade/presentation/library/manga/MangaLibrarySettingsDialog.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAppearanceScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsBrowseScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDownloadScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLightNovelScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsReaderScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTranslationScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/advanced/ClearAnimeDatabaseScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/advanced/ClearDatabaseScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/AnimeExtensionReposScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/MangaExtensionReposScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/data/CreateBackupScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/data/RestoreBackupScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/debug/WorkerInfoScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsAudioScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/player/PlayerSettingsPlayerScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/player/custombutton/PlayerSettingsCustomButtonScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/PlayerSettingsEditorScreen.kt",
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/player/editor/codeeditor/CodeEditScreen.kt",
            "app/src/main/java/eu/kanade/presentation/reader/OrientationSelectDialog.kt",
            "app/src/main/java/eu/kanade/presentation/reader/ReadingModeSelectDialog.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/extension/AnimeExtensionFilterScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/extension/AnimeExtensionsTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/extension/details/AnimeExtensionDetailsScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/anime/MigrateAnimeScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/anime/season/MigrateSeasonSelectScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/search/AnimeSourceSearchScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/search/MigrateAnimeDialog.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/search/MigrateAnimeSearchScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/sources/MigrateAnimeSourceTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/AnimeSourcesFilterScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/AnimeSourcesTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/browse/BrowseAnimeSourceScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/GlobalAnimeSearchScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/extension/MangaExtensionFilterScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/extension/MangaExtensionsTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/extension/details/MangaExtensionDetailsScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/manga/MigrateMangaScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/search/MangaSourceSearchScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/search/MigrateMangaDialog.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/search/MigrateMangaSearchScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/sources/MigrateMangaSourceTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesFilterScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/MangaSourcesTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/browse/BrowseMangaSourceScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/GlobalMangaSearchScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/browse/novel/source/NovelSourcesTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/category/anime/AnimeCategoryTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/category/manga/MangaCategoryTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/anime/DeepLinkAnimeScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/manga/DeepLinkMangaScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/discover/DiscoverScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadsTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/download/anime/AnimeDownloadQueueTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/download/manga/MangaDownloadQueueTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/track/AnimeTrackInfoDialog.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/track/MangaTrackInfoDialog.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/history/HistoriesTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/history/anime/AnimeHistoryTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/history/manga/MangaHistoryTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/library/anime/AnimeLibraryTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/library/duplicate/DuplicateScanScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/library/manga/MangaLibraryTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/more/OnboardingScreen.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/stats/anime/AnimeStatsTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/stats/manga/MangaStatsTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/storage/anime/AnimeStorageTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/storage/manga/MangaStorageTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/updates/anime/AnimeUpdatesTab.kt",
            "app/src/main/java/eu/kanade/tachiyomi/ui/updates/manga/MangaUpdatesTab.kt",
            "app/src/main/java/mihon/feature/upcoming/anime/UpcomingAnimeScreen.kt",
            "app/src/main/java/mihon/feature/upcoming/manga/UpcomingMangaScreen.kt",
        )
        val EXPLICIT_SCOPED_TARGETS = setOf(
            "app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt",
        )
    }
}
