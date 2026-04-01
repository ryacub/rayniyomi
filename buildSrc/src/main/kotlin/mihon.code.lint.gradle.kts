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

tasks.register("checkDeadXmlLayouts") {
    group = "verification"
    description = "Check for unreferenced XML layout files not tracked for migration"
    notCompatibleWithConfigurationCache("Scans file system at execution time")

    doLast {
        // Layouts known to be dead but pending deletion — add names here to suppress false positives
        // during the deletion migration window. Remove entries after the file is deleted.
        val migratedLayouts = emptySet<String>()

        val layoutDir = projectDir.resolve("src/main/res/layout")
        if (!layoutDir.exists()) return@doLast

        val sourceRoots = listOf(
            projectDir.resolve("src/main/java"),
            projectDir.resolve("src/main/kotlin"),
            projectDir.resolve("src/main/res"),
        )

        fun toPascalCaseBinding(layoutName: String): String {
            return layoutName.split("_").joinToString("") { part ->
                part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } + "Binding"
        }

        fun buildSearchPatterns(layoutName: String): List<String> = listOf(
            "R.layout.$layoutName",
            "@layout/$layoutName",
            "${toPascalCaseBinding(layoutName)}.inflate",
            "${toPascalCaseBinding(layoutName)}.bind",
        )

        fun isLayoutReferenced(layoutName: String): Boolean {
            val patterns = buildSearchPatterns(layoutName)
            for (sourceRoot in sourceRoots) {
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

        val violations = mutableListOf<String>()

        layoutDir.listFiles()
            ?.filter { it.extension == "xml" }
            ?.forEach { xmlFile ->
                val layoutName = xmlFile.nameWithoutExtension
                if (layoutName in migratedLayouts) return@forEach
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
                "baseline in buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts."
            )
        } else {
            logger.lifecycle("checkDeadXmlLayouts: No unexpected dead layouts found ✓")
        }
    }
}

tasks.register("checkNoXmlForMigratedScreens") {
    group = "verification"
    description = "Guard against re-introduction of XML layouts for screens migrated to Compose"
    notCompatibleWithConfigurationCache("Scans file system at execution time")

    doLast {
        // Screens that have been migrated to Compose — their XML layouts must never come back.
        // Add entries here whenever a screen completes its Compose migration.
        val migratedScreens = setOf(
            "download_header",
            "download_item",
            "download_list",
            "player_layout",
            "reader_error",
        )

        val layoutDir = projectDir.resolve("src/main/res/layout")
        if (!layoutDir.exists()) return@doLast

        val reintroduced = layoutDir.listFiles()
            ?.filter { it.extension == "xml" && it.nameWithoutExtension in migratedScreens }
            ?: emptyList()

        if (reintroduced.isNotEmpty()) {
            reintroduced.forEach { file ->
                logger.error("COMPOSE_REGRESSION: ${file.name} — this screen has been migrated to Compose; delete the XML layout")
            }
            throw GradleException(
                "Found ${reintroduced.size} XML layout(s) for Compose-migrated screen(s). " +
                "Remove the XML file(s) — these screens must not revert to XML."
            )
        } else {
            logger.lifecycle("checkNoXmlForMigratedScreens: No Compose migration regressions found ✓")
        }
    }
}

tasks.named("check") {
    dependsOn("checkBlockingCalls", "checkDeadXmlLayouts", "checkNoXmlForMigratedScreens")
}
