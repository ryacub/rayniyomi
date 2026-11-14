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

val appGraphParser by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    appGraphParser(kotlinx.compiler.embeddable)
}

val appGraphCheckSource = layout.projectDirectory.file("scripts/app-graph-check/CheckAppGraphLookups.kt")
val appGraphCheckClasses = layout.buildDirectory.dir("app-graph-check")
val compileAppGraphCheck by tasks.registering(JavaExec::class) {
    classpath(appGraphParser)
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.file(appGraphCheckSource)
    outputs.dir(appGraphCheckClasses)
    val parserClasspath = files(appGraphParser)
    val classes = appGraphCheckClasses
    val source = appGraphCheckSource
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-no-stdlib", "-no-reflect", "-classpath", parserClasspath.asPath,
            "-d", classes.get().asFile.path, source.asFile.path,
        )
    })
}

tasks.register<JavaExec>("checkAppGraphLookups") {
    group = "verification"
    description = "Check production Kotlin functions for new appGraph lookups"
    dependsOn(compileAppGraphCheck)
    classpath(appGraphParser, appGraphCheckClasses)
    mainClass.set("mihon.buildlogic.tasks.CheckAppGraphLookups")
    workingDir(layout.projectDirectory)
    if (providers.gradleProperty("printAppGraphBaseline").isPresent) {
        args("--print-baseline")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
