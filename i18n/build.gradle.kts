import mihon.buildlogic.generatedBuildDir
import mihon.buildlogic.tasks.GenerateLocalesConfigTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library")
    kotlin("multiplatform")
    alias(libs.plugins.moko)
}

kotlin {
    androidTarget()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                api(libs.moko.core)
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

val generatedAndroidResourceDir = generatedBuildDir.resolve("android/res")

android {
    namespace = "tachiyomi.i18n"

    sourceSets {
        val main by getting
        main.res.srcDirs(
            "src/commonMain/resources",
            generatedAndroidResourceDir,
        )
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
    }
}

multiplatformResources {
    resourcesPackage.set("tachiyomi.i18n")
}

tasks {
    val localesConfigTask = register<GenerateLocalesConfigTask>("generateLocalesConfig") {
        mokoResourcesTree = fileTree("$projectDir/src/commonMain/moko-resources/")
        outputResourceDir.set(generatedAndroidResourceDir)
    }
    preBuild {
        dependsOn(localesConfigTask)
    }
}
