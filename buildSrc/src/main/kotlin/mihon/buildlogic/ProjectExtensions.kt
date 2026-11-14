package mihon.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.accessors.dm.LibrariesForAndroidx
import org.gradle.accessors.dm.LibrariesForCompose
import org.gradle.accessors.dm.LibrariesForKotlinx
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

val Project.androidx get() = the<LibrariesForAndroidx>()
val Project.compose get() = the<LibrariesForCompose>()
val Project.kotlinx get() = the<LibrariesForKotlinx>()
val Project.libs get() = the<LibrariesForLibs>()

internal fun Project.configureAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = AndroidConfig.COMPILE_SDK
        buildToolsVersion = AndroidConfig.BUILD_TOOLS
        ndkVersion = AndroidConfig.NDK

        defaultConfig.apply {
            minSdk = AndroidConfig.MIN_SDK
        }

        compileOptions.apply {
            sourceCompatibility = AndroidConfig.JavaVersion
            targetCompatibility = AndroidConfig.JavaVersion
            isCoreLibraryDesugaringEnabled = true
        }
    }

    val strictKotlinWarnings = path in setOf(":domain", ":data", ":core:common")
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(AndroidConfig.JvmTarget)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")

            // Other modules can opt in with the warningsAsErrors Gradle property.
            val warningsAsErrors: String? by project
            allWarningsAsErrors.set(strictKotlinWarnings || warningsAsErrors.toBoolean())

        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.desugar)
    }
}

internal fun Project.configureCompose(commonExtension: CommonExtension) {
    pluginManager.apply(kotlinx.plugins.compose.compiler.get().pluginId)

    commonExtension.apply {
        buildFeatures.apply {
            compose = true
        }

        dependencies {
            "implementation"(platform(compose.bom))
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

        val rootBuildDir = rootProject.layout.buildDirectory.asFile.get()
        val relativePath = projectDir.relativeTo(rootDir)

        if (enableMetrics) {
            rootBuildDir.resolve("compose-metrics").resolve(relativePath).let(metricsDestination::set)
        }

        if (enableReports) {
            rootBuildDir.resolve("compose-reports").resolve(relativePath).let(reportsDestination::set)
        }
    }
}

internal fun Project.configureTest() {
    val runningInCi = providers.environmentVariable("CI").map { it == "true" }.getOrElse(false)
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            if (runningInCi) {
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }

    // Gradle 9 requires junit-platform-launcher on the test runtime classpath.
    // The launcher has no catalogue version, so the BOM supplies it in modules with no test dependencies.
    // Capture libs before entering the configuration lambda (no Project receiver inside).
    val junitBom = libs.junit.bom
    val junitPlatformLauncher = libs.junit.platform.launcher
    configurations.matching { it.name == "testRuntimeOnly" }.configureEach {
        dependencies {
            add(name, platform(junitBom))
            add(name, junitPlatformLauncher)
        }
    }
}

val Project.generatedBuildDir: File get() = project.layout.buildDirectory.asFile.get().resolve("generated/mihon")
