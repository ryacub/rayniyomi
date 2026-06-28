package eu.kanade.presentation.more.settings.screen.browse

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ExtensionReposScreenContentOwnershipTest {

    @Test
    fun `anime and manga repo screens remain typed entrypoints`() {
        val animeScreen = source(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/AnimeExtensionReposScreen.kt",
        )
        val mangaScreen = source(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/MangaExtensionReposScreen.kt",
        )

        assertTrue(animeScreen.contains("class AnimeExtensionReposScreen("))
        assertTrue(animeScreen.contains("AnimeExtensionRepoDependencies("))
        assertTrue(mangaScreen.contains("class MangaExtensionReposScreen("))
        assertTrue(mangaScreen.contains("MangaExtensionRepoDependencies("))
    }

    @Test
    fun `shared repo screen content owns duplicated presentation orchestration`() {
        val sharedContent = source(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/ExtensionReposScreenContent.kt",
        )
        val animeScreen = source(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/AnimeExtensionReposScreen.kt",
        )
        val mangaScreen = source(
            "app/src/main/java/eu/kanade/presentation/more/settings/screen/browse/MangaExtensionReposScreen.kt",
        )

        assertTrue(sharedContent.contains("internal fun ExtensionReposScreenContent("))
        assertTrue(sharedContent.contains("ExtensionRepoCreateDialog("))
        assertTrue(sharedContent.contains("ExtensionRepoDeleteDialog("))
        assertTrue(sharedContent.contains("ExtensionRepoConflictDialog("))
        assertTrue(sharedContent.contains("ExtensionRepoConfirmDialog("))
        assertTrue(animeScreen.contains("ExtensionReposScreenContent("))
        assertTrue(mangaScreen.contains("ExtensionReposScreenContent("))
        assertFalse(animeScreen.contains("ExtensionRepoCreateDialog("))
        assertFalse(mangaScreen.contains("ExtensionRepoCreateDialog("))
    }

    private fun source(path: String): String {
        val root = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        return Files.readString(root.resolve(path))
    }
}
