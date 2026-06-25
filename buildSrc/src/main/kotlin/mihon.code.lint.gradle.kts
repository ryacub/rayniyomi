import org.gradle.accessors.dm.LibrariesForLibs
import mihon.buildlogic.tasks.CheckBlockingCallsTask
import mihon.buildlogic.tasks.CheckDeadXmlLayoutsTask
import mihon.buildlogic.tasks.CheckNoXmlForMigratedScreensTask

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

tasks.register<CheckBlockingCallsTask>("checkBlockingCalls") {
    group = "verification"
    description = "Check for runBlocking usage in UI-layer code"
    sourceDir.set(layout.projectDirectory.dir("src/main/java"))
    uiPaths.set(
        listOf(
            "eu/kanade/tachiyomi/ui",
            "eu/kanade/presentation",
        ),
    )
    // Pre-existing violations baselined before guardrail was added.
    // Remove entries as they are fixed in dedicated tickets.
    baseline.set(listOf("ReaderChapterListManager.kt"))
}

tasks.register<CheckDeadXmlLayoutsTask>("checkDeadXmlLayouts") {
    group = "verification"
    description = "Check for unreferenced XML layout files not tracked for migration"
    layoutDir.set(layout.projectDirectory.dir("src/main/res/layout"))
    sourceRoots.from(
        layout.projectDirectory.dir("src/main/java"),
        layout.projectDirectory.dir("src/main/kotlin"),
        layout.projectDirectory.dir("src/main/res"),
    )
    // Layouts known to be dead but pending deletion during the deletion migration window.
    migratedLayouts.set(emptyList())
}

tasks.register<CheckNoXmlForMigratedScreensTask>("checkNoXmlForMigratedScreens") {
    group = "verification"
    description = "Guard against re-introduction of XML layouts for screens migrated to Compose"

    layoutDir.set(layout.projectDirectory.dir("src/main/res/layout"))
    // Screens that have been migrated to Compose — their XML layouts must never come back.
    // Add entries here whenever a screen completes its Compose migration.
    migratedScreens.set(
        listOf(
            "download_header",
            "download_item",
            "download_list",
            "player_layout",
            "reader_error",
        ),
    )
}

tasks.named("check") {
    dependsOn("checkBlockingCalls", "checkDeadXmlLayouts", "checkNoXmlForMigratedScreens")
}
