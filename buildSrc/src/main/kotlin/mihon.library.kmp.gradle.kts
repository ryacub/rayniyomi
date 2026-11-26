import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureTest
import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.kotlin.multiplatform.library")
    kotlin("multiplatform")

    id("mihon.code.lint")
}

configure<KotlinMultiplatformExtension> {
    extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
        compileSdk {
            version = release(AndroidConfig.COMPILE_SDK) { minorApiLevel = AndroidConfig.COMPILE_SDK_MINOR }
        }
        minSdk = AndroidConfig.MIN_SDK
        enableCoreLibraryDesugaring = true
    }

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        val warningsAsErrors: String? by project
        allWarningsAsErrors.set(warningsAsErrors.toBoolean())
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(AndroidConfig.JvmTarget)
    }
}

val kmpDesugarDep = the<LibrariesForLibs>().desugar

dependencies {
    "coreLibraryDesugaring"(kmpDesugarDep)
}

configureTest()
