import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

val xmlFormatExclude = buildList(2) {
    add("**/build/**/*.xml")

    projectDir
        .resolve("src/commonMain/moko-resources")
        .takeIf { it.isDirectory }
        ?.let(::fileTree)
        ?.matching { exclude("/base/**") }
        ?.let(::add)
}
    .toTypedArray()

spotless {
    kotlin {
        target("**/*.kt", "**/*.kts")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("**/*.xml")
        targetExclude(*xmlFormatExclude)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("checkBlockingCalls") {
    group = "verification"
    description = "Check for runBlocking usage in UI-layer code"

    doLast {
        val violations = mutableListOf<String>()
        val uiPaths = listOf(
            "eu/kanade/tachiyomi/ui",
            "eu/kanade/presentation",
        )

        // Pre-existing violations baselined before guardrail was added.
        // Remove entries as they are fixed in dedicated tickets.
        val baseline = setOf(
            "ReaderChapterListManager.kt",
        )

        val sourceDir = projectDir.resolve("src/main/java")
        if (!sourceDir.exists()) return@doLast

        sourceDir.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> uiPaths.any { file.path.contains(it) } }
            .filter { file -> !file.path.contains("/test/") && !file.path.contains("/androidTest/") }
            .filter { file -> file.name !in baseline }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.contains("runBlocking") &&
                        !trimmed.startsWith("import ") &&
                        !trimmed.startsWith("//") &&
                        !trimmed.startsWith("*") &&
                        !trimmed.startsWith("/*")) {
                        violations.add("${file.relativeTo(projectDir)}:${index + 1}: $trimmed")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            violations.forEach { logger.error("BLOCKING_CALL: $it") }
            throw GradleException(
                "Found ${violations.size} runBlocking usage(s) in UI-layer code. " +
                "Use coroutine scopes instead (viewModelScope.launchIO, lifecycleScope)."
            )
        } else {
            logger.lifecycle("checkBlockingCalls: No blocking calls found in UI-layer code ✓")
        }
    }
}
