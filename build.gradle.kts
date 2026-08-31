import mihon.buildlogic.tasks.CheckNullAssertionsTask

plugins {
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

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
