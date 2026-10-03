package eu.kanade.tachiyomi.ui.browse

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.streams.asSequence

class MigrationDialogViewModelOwnershipTest {

    private val projectRoot = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }

    @Test
    fun `migration dialog ViewModels are acquired from the Compose owner`() {
        val directConstruction = Regex(
            """screenModel\s*=\s*Migrate(?:Anime|Manga)DialogScreenModel\(\)""",
        )
        val violations = Files.walk(projectRoot.resolve("app/src/main/java")).use { paths ->
            paths.asSequence()
                .filter { it.toString().endsWith(".kt") }
                .flatMap { path ->
                    val relativePath = projectRoot.relativize(path)
                    val source = path.readText()
                    directConstruction.findAll(source)
                        .map { match ->
                            val line = source.take(match.range.first).count { it == '\n' } + 1
                            "$relativePath:$line: ${match.value}"
                        }
                }
                .toList()
        }

        assertFalse(
            violations.isNotEmpty(),
            "Migration dialog ViewModels must use viewModel { ... }:\n${violations.joinToString("\n")}",
        )
    }
}
