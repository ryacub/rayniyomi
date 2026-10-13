import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import java.util.zip.ZipFile

plugins {
    id("mihon.android.application")
    id("mihon.android.application.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "xyz.rayniyomi.cloudstream"

    defaultConfig {
        applicationId = "xyz.rayniyomi.cloudstream"
        versionCode = 1
        // The host reads the extension lib version from the part before the last dot.
        versionName = "16.1"
    }

    // CI signs release builds with the PLUGIN_SIGNING_KEY secret, as for lightnovel-plugin.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("PLUGIN_KEYSTORE_PATH") ?: "/dev/null")
            storePassword = System.getenv("PLUGIN_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("PLUGIN_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("PLUGIN_KEY_PASSWORD") ?: ""
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("PLUGIN_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }
}

val sharedPrefixesFile = file("shared-prefixes.txt")

abstract class GenerateSharedPrefixesTask : DefaultTask() {
    @get:InputFile
    abstract val prefixesFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val prefixes = prefixesFile.get().asFile.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val source = buildString {
            appendLine("package xyz.rayniyomi.cloudstream;")
            appendLine()
            appendLine("final class SharedPrefixes {")
            appendLine("    static final String[] VALUES = {")
            prefixes.forEach { appendLine("        \"$it\",") }
            appendLine("    };")
            appendLine()
            appendLine("    private SharedPrefixes() {}")
            appendLine("}")
        }
        val directory = outputDirectory.get().asFile.resolve("xyz/rayniyomi/cloudstream")
        directory.mkdirs()
        directory.resolve("SharedPrefixes.java").writeText(source)
    }
}

abstract class CopyHostRuntimeTask : DefaultTask() {
    @get:InputFiles
    abstract val hostRuntimeJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        hostRuntimeJar.singleFile.copyTo(target.resolve("host-runtime.jar"))
    }
}

abstract class VerifyNoHostClassesTask : DefaultTask() {
    @get:InputFiles
    abstract val classJars: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbidden = listOf("eu/kanade/", "tachiyomi/", "uy/kohesive/")
        val leaks = classJars.files.filter { it.isFile && it.extension == "jar" }.flatMap { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { name -> name.endsWith(".class") && forbidden.any(name::startsWith) }
                    .map { "${jar.name}: $it" }
                    .toList()
            }
        }
        check(leaks.isEmpty()) {
            "The CloudStream runtime APK would contain host API classes:\n" + leaks.take(50).joinToString("\n")
        }
    }
}

val hostRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val generateSharedPrefixes = tasks.register<GenerateSharedPrefixesTask>("generateSharedPrefixes") {
    prefixesFile.set(sharedPrefixesFile)
}

val copyHostRuntime = tasks.register<CopyHostRuntimeTask>("copyHostRuntime") {
    hostRuntimeJar.from(hostRuntime)
}

val verifyNoHostClasses = tasks.register<VerifyNoHostClassesTask>("verifyNoHostClasses") {
    group = "verification"
    classJars.from(
        configurations.named("releaseRuntimeClasspath").map { classpath ->
            classpath.incoming.artifactView {
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar")
            }.files
        },
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateSharedPrefixes,
            GenerateSharedPrefixesTask::outputDirectory,
        )
        variant.sources.assets?.addGeneratedSourceDirectory(copyHostRuntime, CopyHostRuntimeTask::outputDirectory)
    }
}

tasks.named("check") {
    dependsOn(verifyNoHostClasses)
}

dependencies {
    hostRuntime(project(path = ":cloudstream-runtime-host", configuration = "hostRuntime"))

    implementation(project(":cloudstream-runtime-contract"))
    compileOnly(project(":source-api"))

    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)
    implementation(androidx.corektx)
    implementation(androidx.lifecycle.runtimecompose)
    implementation(androidx.lifecycle.viewmodelcompose)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.coroutines.android)
    implementation(kotlinx.serialization.json)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)

    testImplementation(project(":source-api"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
