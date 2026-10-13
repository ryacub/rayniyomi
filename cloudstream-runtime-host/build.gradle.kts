import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// This module builds an APK that is never installed. Its dex and Java resources become
// assets/host-runtime.jar in :cloudstream-runtime, which the host app loads in its own process.
plugins {
    id("mihon.android.application")
    kotlin("plugin.serialization")
}

android {
    namespace = "xyz.rayniyomi.cloudstream.host"

    defaultConfig {
        applicationId = "xyz.rayniyomi.cloudstream.host"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

val hostSharedPrefixes = rootProject.file("cloudstream-runtime/shared-prefixes.txt")
    .readLines()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }

// The host provides these libraries. The runtime jar must use the host's copies.
configurations
    .matching { it.name.endsWith("RuntimeClasspath") && "Test" !in it.name }
    .configureEach {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
        exclude(group = "com.squareup.okhttp3")
        exclude(group = "com.squareup.okio")
        exclude(group = "androidx.annotation")
        exclude(group = "org.json")
        exclude(group = "com.google.code.findbugs")
    }

dependencies {
    implementation(project(":cloudstream-runtime-contract"))
    implementation(libs.cloudstream.library)
    implementation(kotlinx.serialization.json)

    compileOnly(project(":source-api"))
    compileOnly(project(":core:common"))
    compileOnly(platform(kotlinx.coroutines.bom))
    compileOnly(kotlinx.coroutines.android)
    compileOnly(platform(libs.okhttp.bom))
    compileOnly(libs.okhttp.core)
    compileOnly(libs.injekt)
    compileOnly(libs.preferencektx)

    testImplementation(project(":source-api"))
    testImplementation(project(":core:common"))
    testImplementation(platform(kotlinx.coroutines.bom))
    testImplementation(kotlinx.coroutines.test)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
}

abstract class HostRuntimeJarTask : DefaultTask() {
    @get:InputFiles
    abstract val apkDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun build() {
        val apk = apkDirectory.get().asFile.listFiles().orEmpty().single { it.extension == "apk" }
        ZipFile(apk).use { source ->
            ZipOutputStream(outputJar.get().asFile.outputStream()).use { target ->
                source.entries().asSequence()
                    .filter { !it.isDirectory && isRuntimeEntry(it.name) }
                    .forEach { entry ->
                        target.putNextEntry(ZipEntry(entry.name))
                        source.getInputStream(entry).use { it.copyTo(target) }
                        target.closeEntry()
                    }
            }
        }
    }

    private fun isRuntimeEntry(name: String): Boolean = when {
        name.startsWith("classes") && name.endsWith(".dex") -> true
        name == "AndroidManifest.xml" || name == "resources.arsc" -> false
        name.startsWith("res/") || name.startsWith("assets/") || name.startsWith("lib/") -> false
        name.startsWith("META-INF/com/android/") -> false
        name.startsWith("META-INF/") && name.substringAfterLast('.') in setOf("SF", "RSA", "EC", "MF") -> false
        else -> true
    }
}

abstract class VerifyHostRuntimeClassesTask : DefaultTask() {
    @get:InputFiles
    abstract val classJars: ConfigurableFileCollection

    @get:Input
    abstract val sharedPrefixes: ListProperty<String>

    @TaskAction
    fun verify() {
        val prefixes = sharedPrefixes.get()
        val leaks = classJars.files.filter { it.isFile && it.extension == "jar" }.flatMap { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.startsWith("META-INF/") }
                    .map { it.removeSuffix(".class").replace('/', '.') }
                    .filter { name -> prefixes.any(name::startsWith) }
                    .map { "${jar.name}: $it" }
                    .toList()
            }
        }
        check(leaks.isEmpty()) {
            "The CloudStream runtime jar would contain host-provided classes:\n" + leaks.take(50).joinToString("\n")
        }
    }
}

val verifyHostRuntimeClasses = tasks.register<VerifyHostRuntimeClassesTask>("verifyHostRuntimeClasses") {
    group = "verification"
    sharedPrefixes.set(hostSharedPrefixes)
    classJars.from(
        configurations.named("releaseRuntimeClasspath").map { classpath ->
            classpath.incoming.artifactView {
                attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "android-classes-jar")
            }.files
        },
    )
}

val hostRuntimeJar = tasks.register<HostRuntimeJarTask>("hostRuntimeJar") {
    dependsOn(verifyHostRuntimeClasses)
    outputJar.set(layout.buildDirectory.file("host-runtime/host-runtime.jar"))
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        hostRuntimeJar.configure {
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
        }
    }
}

val hostRuntime by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(hostRuntime.name, hostRuntimeJar.flatMap { it.outputJar })
}

tasks.named("check") {
    dependsOn(verifyHostRuntimeClasses)
}
