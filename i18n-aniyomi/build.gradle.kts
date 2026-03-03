import mihon.buildlogic.generatedBuildDir
import mihon.buildlogic.tasks.GenerateLocalesConfigTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library.kmp")
    alias(libs.plugins.moko)
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "tachiyomi.i18n.aniyomi"
        lint {
            disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        }
    }

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

multiplatformResources {
    resourcesClassName.set("AYMR")
    resourcesPackage.set("tachiyomi.i18n.aniyomi")
}

val localesConfigTask = tasks.register<GenerateLocalesConfigTask>("generateLocalesConfig") {
    mokoResourcesTree = fileTree("$projectDir/src/commonMain/moko-resources/")
    outputResourceDir.set(generatedAndroidResourceDir)
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/commonMain/resources")
        variant.sources.res?.addGeneratedSourceDirectory(
            localesConfigTask,
            GenerateLocalesConfigTask::outputResourceDir,
        )
    }
}
