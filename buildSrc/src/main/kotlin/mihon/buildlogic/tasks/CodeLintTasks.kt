package mihon.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CheckNullAssertionsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val baseline = baselineFile.get().asFile.readLines()
            .asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .groupingBy { it }
            .eachCount()
            .toMutableMap()
        val projectDir = projectDirectory.get().asFile
        val violations = mutableListOf<String>()

        sourceFiles.asFileTree.matching { include("**/*.kt") }
            .files
            .sortedBy { it.path }
            .forEach { file ->
                val lines = file.readLines()
                val relativePath = file.relativeTo(projectDir).invariantSeparatorsPath
                findNullAssertions(lines).forEach { lineNumber ->
                    val line = lines[lineNumber - 1].trim()
                    val fingerprint = "$relativePath\t$line"
                    val remaining = baseline[fingerprint] ?: 0
                    if (remaining == 0) {
                        violations.add("$relativePath:$lineNumber: $line")
                    } else {
                        baseline[fingerprint] = remaining - 1
                    }
                }
            }

        val staleBaseline = baseline.filterValues { it > 0 }.keys
        if (violations.isNotEmpty() || staleBaseline.isNotEmpty()) {
            violations.forEach { logger.error("NULL_ASSERTION: $it") }
            staleBaseline.forEach { logger.error("STALE_NULL_ASSERTION_BASELINE: $it") }
            throw GradleException(
                "Found ${violations.size} new production null assertion(s) and " +
                    "${staleBaseline.sumOf { baseline[it] ?: 0 }} stale baseline entry(ies). " +
                    "Remove `!!`, or update scripts/null_assertion_baseline.txt only for " +
                    "pre-existing assertions. See docs/null-assertion-baseline.md for " +
                    "baseline maintenance rules.",
            )
        } else {
            logger.lifecycle("checkNullAssertions: No new production null assertions found ✓")
        }
    }

    private fun findNullAssertions(lines: List<String>): List<Int> =
        maskCommentsAndLiterals(lines).flatMapIndexed { index, line ->
            List(Regex("!!").findAll(line).count()) { index + 1 }
        }
}

abstract class CheckCancellationCatchesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val baseline = baselineFile.get().asFile.readLines()
            .asSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .groupingBy { it }
            .eachCount()
            .toMutableMap()
        val projectDir = projectDirectory.get().asFile
        val violations = mutableListOf<String>()

        sourceFiles.asFileTree.matching { include("**/*.kt") }
            .files
            .sortedBy { it.path }
            .forEach { file ->
                val lines = file.readLines()
                val relativePath = file.relativeTo(projectDir).invariantSeparatorsPath
                findCatchesThatSwallowCancellation(lines).forEach { lineNumber ->
                    val line = lines[lineNumber - 1].trim()
                    val fingerprint = "$relativePath\t$line"
                    val remaining = baseline[fingerprint] ?: 0
                    if (remaining == 0) {
                        violations.add("$relativePath:$lineNumber: $line")
                    } else {
                        baseline[fingerprint] = remaining - 1
                    }
                }
            }

        val staleBaseline = baseline.filterValues { it > 0 }.keys
        if (violations.isNotEmpty() || staleBaseline.isNotEmpty()) {
            violations.forEach { logger.error("CANCELLATION_CATCH: $it") }
            staleBaseline.forEach { logger.error("STALE_CANCELLATION_CATCH_BASELINE: $it") }
            throw GradleException(
                "Found ${violations.size} new catch(es) of Throwable or Exception that do not rethrow " +
                    "CancellationException and ${staleBaseline.sumOf { baseline[it] ?: 0 }} stale " +
                    "baseline entry(ies). Rethrow cancellation or catch a narrower type. Update " +
                    "scripts/cancellation_catch_baseline.txt only to remove fixed sites. See " +
                    "docs/cancellation-catch-baseline.md.",
            )
        } else {
            logger.lifecycle("checkCancellationCatches: No new catches that swallow cancellation found ✓")
        }
    }

    private fun findCatchesThatSwallowCancellation(lines: List<String>): List<Int> {
        val code = maskCommentsAndLiterals(lines).joinToString("\n")
        return GENERIC_CATCH.findAll(code)
            .filterNot { it.rethrowsCancellation(code) }
            .map { catch -> code.substring(0, catch.range.first).count { it == '\n' } + 1 }
            .toList()
    }

    private fun MatchResult.rethrowsCancellation(code: String): Boolean {
        val name = groupValues[1]
        val firstStatement = Regex(
            """^\s*if\s*\(\s*$name\s+is\s+$CANCELLATION_TYPE\s*\)\s*(\{\s*)?throw\s+$name\b""",
        )
        return firstStatement.containsMatchIn(code.substring(range.last + 1)) ||
            followsCancellationClause(code, range.first)
    }

    private fun followsCancellationClause(code: String, catchStart: Int): Boolean {
        val closingBrace = code.lastIndexOfAny(charArrayOf('}'), catchStart - 1)
        if (closingBrace < 0 || code.substring(closingBrace + 1, catchStart).isNotBlank()) return false
        var depth = 0
        for (index in closingBrace downTo 0) {
            when (code[index]) {
                '}' -> depth++
                '{' -> if (--depth == 0) return CANCELLATION_CLAUSE.containsMatchIn(code.substring(0, index))
            }
        }
        return false
    }

    private companion object {
        const val CANCELLATION_TYPE = """(?:[\w.]+\.)?CancellationException"""
        val GENERIC_CATCH = Regex("""catch\s*\(\s*(\w+)\s*:\s*(?:Throwable|Exception)\s*\)\s*\{""")
        val CANCELLATION_CLAUSE = Regex("""catch\s*\(\s*\w+\s*:\s*$CANCELLATION_TYPE\s*\)\s*$""")
    }
}

/**
 * Returns [lines] with comments and string and character literals replaced by spaces, so a
 * search of the result sees only code. Line count and column positions stay the same.
 */
internal fun maskCommentsAndLiterals(lines: List<String>): List<String> {
    var state = ScanState.CODE
    var blockCommentDepth = 0

    return lines.map { line ->
        val masked = StringBuilder(line.length)
        var offset = 0
        fun keep(count: Int) {
            masked.append(line, offset, minOf(offset + count, line.length))
            offset += count
        }
        fun mask(count: Int) {
            repeat(minOf(count, line.length - offset)) { masked.append(' ') }
            offset += count
        }
        while (offset < line.length) {
            when (state) {
                ScanState.CODE -> when {
                    line.startsWith("//", offset) -> mask(line.length - offset)
                    line.startsWith("/*", offset) -> {
                        state = ScanState.BLOCK_COMMENT
                        blockCommentDepth = 1
                        mask(2)
                    }
                    line.startsWith("\"\"\"", offset) -> {
                        state = ScanState.TRIPLE_QUOTE
                        mask(3)
                    }
                    line[offset] == '\"' -> {
                        state = ScanState.STRING
                        mask(1)
                    }
                    line[offset] == '\'' -> {
                        state = ScanState.CHARACTER
                        mask(1)
                    }
                    else -> keep(1)
                }
                ScanState.BLOCK_COMMENT -> when {
                    line.startsWith("/*", offset) -> {
                        blockCommentDepth += 1
                        mask(2)
                    }
                    line.startsWith("*/", offset) -> {
                        blockCommentDepth -= 1
                        mask(2)
                        if (blockCommentDepth == 0) state = ScanState.CODE
                    }
                    else -> mask(1)
                }
                ScanState.TRIPLE_QUOTE -> if (line.startsWith("\"\"\"", offset)) {
                    state = ScanState.CODE
                    mask(3)
                } else {
                    mask(1)
                }
                ScanState.STRING -> when {
                    line[offset] == '\\' -> mask(2)
                    line[offset] == '\"' -> {
                        state = ScanState.CODE
                        mask(1)
                    }
                    else -> mask(1)
                }
                ScanState.CHARACTER -> when {
                    line[offset] == '\\' -> mask(2)
                    line[offset] == '\'' -> {
                        state = ScanState.CODE
                        mask(1)
                    }
                    else -> mask(1)
                }
            }
        }
        if (state == ScanState.STRING || state == ScanState.CHARACTER) {
            state = ScanState.CODE
        }
        masked.toString()
    }
}

private enum class ScanState {
    CODE,
    BLOCK_COMMENT,
    TRIPLE_QUOTE,
    STRING,
    CHARACTER,
}

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
