import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("mihon.library.kmp")
    kotlin("plugin.serialization")
}

kotlin {
    androidLibrary {
        namespace = "eu.kanade.tachiyomi.source"
        optimization {
            consumerKeepRules.files("consumer-proguard.pro")
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(libs.injekt)
                api(libs.rxjava)
                api(libs.jsoup)

                implementation(project.dependencies.platform(compose.bom))
                implementation(compose.runtime)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core.common)
                api(libs.preferencektx)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
