package mihon.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CheckBlockingCallsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val uiPaths: ListProperty<String>

    @get:Input
    abstract val baseline: ListProperty<String>

    @TaskAction
    fun check() {
        val violations = mutableListOf<String>()
        val sourceDirFile = sourceDir.get().asFile
        if (!sourceDirFile.exists()) return

        val uiPathValues = uiPaths.get()
        val baselineValues = baseline.get().toSet()
        val projectDir = sourceDirFile.parentFile.parentFile.parentFile

        sourceDirFile.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> uiPathValues.any { file.path.contains(it) } }
            .filter { file -> !file.path.contains("/test/") && !file.path.contains("/androidTest/") }
            .filter { file -> file.name !in baselineValues }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.contains("runBlocking") &&
                        !trimmed.startsWith("import ") &&
                        !trimmed.startsWith("//") &&
                        !trimmed.startsWith("*") &&
                        !trimmed.startsWith("/*")
                    ) {
                        violations.add("${file.relativeTo(projectDir)}:${index + 1}: $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error("BLOCKING_CALL: $it") }
            throw GradleException(
                "Found ${violations.size} runBlocking usage(s) in UI-layer code. " +
                    "Use coroutine scopes instead (viewModelScope.launchIO, lifecycleScope).",
            )
        } else {
            logger.lifecycle("checkBlockingCalls: No blocking calls found in UI-layer code ✓")
        }
    }
}

abstract class CheckDeadXmlLayoutsTask : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val layoutDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceRoots: ConfigurableFileCollection

    @get:Input
    abstract val migratedLayouts: ListProperty<String>

    @TaskAction
    fun check() {
        val layoutDirFile = layoutDir.get().asFile
        if (!layoutDirFile.exists()) return

        val migratedLayoutValues = migratedLayouts.get().toSet()
        val violations = mutableListOf<String>()

        layoutDirFile.listFiles()
            ?.filter { it.extension == "xml" }
            ?.forEach { xmlFile ->
                val layoutName = xmlFile.nameWithoutExtension
                if (layoutName in migratedLayoutValues) return@forEach
                if (!isLayoutReferenced(layoutName)) {
                    violations.add(layoutName)
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { name ->
                logger.error("DEAD_LAYOUT: $name — not referenced in any source file")
            }
            throw GradleException(
                "Found ${violations.size} unreferenced XML layout(s). " +
                    "Either reference the layout, delete it, or add it to the migratedLayouts " +
                    "baseline in buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts.",
            )
        } else {
            logger.lifecycle("checkDeadXmlLayouts: No unexpected dead layouts found ✓")
        }
    }

    private fun isLayoutReferenced(layoutName: String): Boolean {
        val patterns = buildSearchPatterns(layoutName)
        for (sourceRoot in sourceRoots.files) {
            if (!sourceRoot.exists()) continue
            sourceRoot.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
                .forEach { file ->
                    val content = file.readText()
                    if (patterns.any { content.contains(it) }) return true
                }
        }
        return false
    }

    private fun buildSearchPatterns(layoutName: String): List<String> = listOf(
        "R.layout.$layoutName",
        "@layout/$layoutName",
        "${toPascalCaseBinding(layoutName)}.inflate",
        "${toPascalCaseBinding(layoutName)}.bind",
    )

    private fun toPascalCaseBinding(layoutName: String): String {
        return layoutName.split("_").joinToString("") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } + "Binding"
    }
}

abstract class CheckNoXmlForMigratedScreensTask : DefaultTask() {

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val layoutDir: DirectoryProperty

    @get:Input
    abstract val migratedScreens: ListProperty<String>

    @TaskAction
    fun check() {
        val layoutDirFile = layoutDir.get().asFile
        if (!layoutDirFile.exists()) return

        val migratedScreenValues = migratedScreens.get().toSet()
        val reintroduced = layoutDirFile.listFiles()
            ?.filter { it.extension == "xml" && it.nameWithoutExtension in migratedScreenValues }
            ?: emptyList()

        if (reintroduced.isNotEmpty()) {
            reintroduced.forEach { file ->
                logger.error("COMPOSE_REGRESSION: ${file.name} — this screen has been migrated to Compose; delete the XML layout")
            }
            throw GradleException(
                "Found ${reintroduced.size} XML layout(s) for Compose-migrated screen(s). " +
                    "Remove the XML file(s) — these screens must not revert to XML.",
            )
        } else {
            logger.lifecycle("checkNoXmlForMigratedScreens: No Compose migration regressions found ✓")
        }
    }
}
