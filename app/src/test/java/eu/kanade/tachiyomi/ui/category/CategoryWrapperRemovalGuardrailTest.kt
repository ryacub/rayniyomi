package eu.kanade.tachiyomi.ui.category

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.streams.asSequence

class CategoryWrapperRemovalGuardrailTest {

    private val projectRoot = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { it.resolve("settings.gradle.kts").exists() }

    @Test
    fun `anime and manga category wrappers are not reintroduced`() {
        val removedWrappers = listOf(
            projectRoot.resolve("app/src/main/java/eu/kanade/presentation/category/AnimeCategoryScreen.kt"),
            projectRoot.resolve("app/src/main/java/eu/kanade/presentation/category/MangaCategoryScreen.kt"),
        )

        removedWrappers.forEach { path ->
            assertFalse(path.exists(), "Category wrapper should stay removed: $path")
        }
    }

    @Test
    fun `category tabs call shared category screen directly`() {
        val sourceReferences = Files.walk(projectRoot.resolve("app/src/main/java")).use { paths ->
            paths.asSequence()
                .filter { it.toString().endsWith(".kt") }
                .flatMap { path ->
                    val relativePath = projectRoot.relativize(path).toString()
                    path.readText()
                        .lineSequence()
                        .mapIndexedNotNull { index, line ->
                            val referencesRemovedWrapper =
                                line.contains("eu.kanade.presentation.category.AnimeCategoryScreen") ||
                                    line.contains("eu.kanade.presentation.category.MangaCategoryScreen") ||
                                    Regex("""\bAnimeCategoryScreen\s*\(""").containsMatchIn(line) ||
                                    Regex("""\bMangaCategoryScreen\s*\(""").containsMatchIn(line)

                            if (referencesRemovedWrapper) {
                                "$relativePath:${index + 1}: $line"
                            } else {
                                null
                            }
                        }
                }
                .toList()
        }

        assertFalse(
            sourceReferences.isNotEmpty(),
            "Category wrapper references should stay removed:\n${sourceReferences.joinToString("\n")}",
        )
    }
}
