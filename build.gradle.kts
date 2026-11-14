import mihon.buildlogic.tasks.CheckCancellationCatchesTask
import mihon.buildlogic.tasks.CheckNullAssertionsTask

plugins {
    alias(libs.plugins.metro) apply false
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
}

tasks.register<CheckNullAssertionsTask>("checkNullAssertions") {
    group = "verification"
    description = "Check covered production sources for new non-null assertions"
    sourceFiles.from(
        layout.projectDirectory.dir("app/src/main"),
        layout.projectDirectory.dir("data/src/main"),
        layout.projectDirectory.dir("domain/src/main"),
        layout.projectDirectory.dir("source-local/src/commonMain"),
        layout.projectDirectory.dir("source-local/src/androidMain"),
    )
    projectDirectory.set(layout.projectDirectory)
    baselineFile.set(layout.projectDirectory.file("scripts/null_assertion_baseline.txt"))
}

tasks.register<CheckCancellationCatchesTask>("checkCancellationCatches") {
    group = "verification"
    description = "Check production sources for new catches that swallow coroutine cancellation"
    sourceFiles.from(
        layout.projectDirectory.dir("app/src/main"),
        layout.projectDirectory.dir("cloudstream-runtime/src/main"),
        layout.projectDirectory.dir("cloudstream-runtime-contract/src/main"),
        layout.projectDirectory.dir("core/archive/src/main"),
        layout.projectDirectory.dir("core/common/src/main"),
        layout.projectDirectory.dir("core-metadata/src/main"),
        layout.projectDirectory.dir("data/src/main"),
        layout.projectDirectory.dir("domain/src/main"),
        layout.projectDirectory.dir("lightnovel-contract/src/main"),
        layout.projectDirectory.dir("lightnovel-plugin/src/main"),
        layout.projectDirectory.dir("presentation-core/src/main"),
        layout.projectDirectory.dir("presentation-widget/src/main"),
        layout.projectDirectory.dir("source-api/src/androidMain"),
        layout.projectDirectory.dir("source-api/src/commonMain"),
        layout.projectDirectory.dir("source-local/src/androidMain"),
        layout.projectDirectory.dir("source-local/src/commonMain"),
    )
    projectDirectory.set(layout.projectDirectory)
    baselineFile.set(layout.projectDirectory.file("scripts/cancellation_catch_baseline.txt"))
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
