import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import mihon.buildlogic.Config
import mihon.buildlogic.getBuildTime
import mihon.buildlogic.getCommitCount
import mihon.buildlogic.getGitSha

// R39: Fork compliance checklist added to PR template

plugins {
    id("mihon.android.application")
    id("mihon.android.application.compose")
    kotlin("plugin.serialization")
    alias(libs.plugins.aboutLibraries)
    // Google Services: conditional - only release (google-services.json has release package only)
    // Crashlytics: applied unconditionally so the build ID is always embedded in release APKs
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics)
}

val appVersionCode = 272
val lightNovelExpectedPluginApiVersion = 1

// Apply Google Services plugin before AGP variant configuration and only for builds whose
// applicationId is registered in google-services.json.
// google-services.json contains only the release package (xyz.rayniyomi); applying it for
// debug/benchmark would fail because xyz.rayniyomi.dev and xyz.rayniyomi.benchmark are not
// registered.
// The Crashlytics plugin is applied unconditionally in the plugins block above so the build ID
// is reliably embedded in every release APK regardless of Gradle invocation order or cache.
if (
    gradle.startParameter.taskNames.none {
        it.contains("Debug", ignoreCase = true) || it.contains("Benchmark", ignoreCase = true)
    }
) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "eu.kanade.tachiyomi"

    defaultConfig {
        applicationId = "xyz.rayniyomi"

        versionCode = appVersionCode
        versionName = "0.18.1.141"

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")
        buildConfigField("int", "LIGHT_NOVEL_PLUGIN_API_VERSION", lightNovelExpectedPluginApiVersion.toString())

        // R37/R38: This fork uses its own Firebase project for Analytics and Crashlytics
        // (see firebase_config.xml), while ACRA crash reporting remains disabled.
        // This keeps fork telemetry isolated from upstream services.
        // See: docs/adr/0002-fork-isolation-updates-and-telemetry.md
        // See: app/src/main/res/values/firebase_config.xml
        // See: app/src/main/res/values/acra_disabled.xml
        //
        // If you want to enable ACRA for your own fork, uncomment and configure:
        // val acraProperties = Properties()
        // rootProject.file("acra.properties")
        //     .takeIf { it.exists() }
        //     ?.let { acraProperties.load(FileInputStream(it)) }
        // val acraUri = acraProperties.getProperty("ACRA_URI", "")
        // val acraLogin = acraProperties.getProperty("ACRA_LOGIN", "")
        // val acraPassword = acraProperties.getProperty("ACRA_PASSWORD", "")
        // buildConfigField("String", "ACRA_URI", ""$acraUri"")
        // buildConfigField("String", "ACRA_LOGIN", ""$acraLogin"")
        // buildConfigField("String", "ACRA_PASSWORD", ""$acraPassword"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        val debug by getting {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-${getCommitCount()}"
            isPseudoLocalesEnabled = true
        }
        val release by getting {
            isMinifyEnabled = Config.enableCodeShrink
            isShrinkResources = Config.enableCodeShrink

            proguardFiles("proguard-android-optimize.txt", "proguard-rules.pro")

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = true)}\"")
        }

        val commonMatchingFallbacks = listOf(release.name)

        create("preview") {
            initWith(release)

            applicationIdSuffix = ".debug"

            versionNameSuffix = debug.versionNameSuffix
            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        }
        create("benchmark") {
            initWith(release)

            isDebuggable = false
            isProfileable = true
            versionNameSuffix = "-benchmark"
            applicationIdSuffix = ".benchmark"

            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)

            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
                nativeSymbolUploadEnabled = false
            }
        }
    }

    flavorDimensions += "track"
    productFlavors {
        create("stable") { isDefault = true }
    }

    sourceSets {
        getByName("preview") { res.srcDir("src/debug/res") }
        getByName("benchmark") { res.srcDir("src/debug/res") }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path",
                "libarchive-jni",
                "libavcodec",
                "libavdevice",
                "libavfilter",
                "libavformat",
                "libavutil",
                "libconscrypt_jni",
                "libc++_shared",
                "libffmpegkit_abidetect",
                "libffmpegkit",
                "libimagedecoder",
                "libmpv",
                "libplayer",
                "libpostproc",
                "libquickjs",
                "libsqlite3x",
                "libswresample",
                "libswscale",
                "libxml2",
            )
                .map { "**/$it.so" }
        }
        resources {
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/*.properties",
                "META-INF/**/LICENSE.txt",
                "META-INF/*.properties",
                "META-INF/*.version",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
            )
        }
    }

    dependenciesInfo {
        includeInApk = Config.includeDependencyInfo
        includeInBundle = Config.includeDependencyInfo
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true

        // Disable some unused things
        aidl = false
        shaders = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// AGP 9 + Crashlytics plugin does not automatically wire release mapping-file resources into
// mergeResources. Register the per-variant generated dirs after evaluation so release APKs
// contain the Crashlytics build ID metadata required at runtime.
// Variant source sets (e.g. "stableRelease") only exist after AGP processes flavors.
afterEvaluate {
    listOf(
        "stableRelease" to "injectCrashlyticsMappingFileIdStableRelease",
    ).forEach { (sourceSetName, generatedDir) ->
        android.sourceSets.findByName(sourceSetName)
            ?.res?.srcDir("build/generated/res/$generatedDir")
    }
}

tasks.configureEach {
    if (
        name.contains("Release") &&
        (name.contains("Resource") || name.contains("Navigation"))
    ) {
        dependsOn("injectCrashlyticsMappingFileIdStableRelease")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(project(":lightnovel-contract"))

    implementation(projects.i18n)
    implementation(projects.i18nAniyomi)
    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.data)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.presentationWidget)

    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    debugImplementation(compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)

    implementation(androidx.interpolator)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(libs.bundles.sqlite)

    implementation(kotlinx.reflect)
    implementation(kotlinx.immutables)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.constraintlayout)
    implementation(aniyomilibs.compose.constraintlayout)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.viewpager)
    implementation(androidx.profileinstaller)
    implementation(aniyomilibs.mediasession)

    implementation(androidx.bundles.lifecycle)
    implementation(androidx.lifecycle.runtimecompose)

    // Job scheduling
    implementation(androidx.workmanager)

    // RxJava
    implementation(libs.rxjava)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)
    implementation(libs.conscrypt.android) // TLS 1.3 support for Android < 10

    // Data serialization (JSON, protobuf, xml)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)

    // Preferences
    implementation(libs.preferencektx)

    // Dependency injection
    implementation(libs.injekt)

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // UI libraries
    implementation(libs.material)
    implementation(libs.photoview)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.insetter)
    implementation(libs.bundles.richtext)
    implementation(libs.aboutLibraries.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.compose.materialmotion)
    implementation(libs.swipe)
    implementation(libs.compose.webview)
    implementation(libs.compose.grid)
    implementation(libs.reorderable)

    // Logging
    implementation(libs.logcat)

    // Firebase Analytics and Crashlytics
    // google-services plugin is applied conditionally (release only) so the xml resources
    // only exist in release builds; Crashlytics plugin is unconditional so the build ID is
    // always embedded. Debug builds compile fine but Firebase won't initialize (no google_app_id).
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    // Shizuku
    implementation(libs.bundles.shizuku)

    // Tests
    testImplementation(libs.bundles.test)
    testImplementation("androidx.lifecycle:lifecycle-runtime-testing:2.8.7")
    androidTestImplementation(platform(compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation(libs.leakcanary.android)

    implementation(libs.leakcanary.plumber)

    testImplementation(kotlinx.coroutines.test)

    // Cast SDK
    implementation(libs.bundles.cast)

    // mpv-android
    implementation(aniyomilibs.aniyomi.mpv)
    // FFmpeg-kit
    implementation(aniyomilibs.ffmpeg.kit)
    implementation(aniyomilibs.arthenica.smartexceptions)
    // seeker seek bar
    implementation(aniyomilibs.seeker)
    // true type parser
    implementation(aniyomilibs.truetypeparser)
}

androidComponents {
    beforeVariants { variantBuilder ->
        // Keep benchmark builds only for the stable track so macrobenchmark installs
        // the signed/profileable app target instead of falling back to unsigned release.
        if (variantBuilder.buildType == "benchmark") {
            variantBuilder.enable = variantBuilder.productFlavors.containsAll(
                listOf("track" to "stable"),
            )
        }
    }
    onVariants(selector().withFlavor("default" to "standard")) {
        // Only excluding in standard flavor because this breaks
        // Layout Inspector's Compose tree
        it.packaging.resources.excludes.add("META-INF/*.version")
    }
}

tasks.register("printLightNovelCompatibilitySnapshot") {
    group = "verification"
    description = "Prints compatibility snapshot consumed by plugin compatibility CI gates."
    notCompatibleWithConfigurationCache(
        "This ad-hoc script task is used only for CI snapshot output.",
    )
    doLast {
        println(
            """
            {
              "hostVersionCode": $appVersionCode,
              "expectedPluginApiVersion": $lightNovelExpectedPluginApiVersion,
              "defaultHostChannel": "stable"
            }
            """.trimIndent(),
        )
    }
}

buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}
