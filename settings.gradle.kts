pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val regex = "com.android.(library|application)".toRegex()
            if (regex matches requested.id.id) {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
    repositories {
        // Temporary for SqlDelight 2.3.0-SNAPSHOT AGP 9 support.
        // Remove this mavenLocal/snapshot wiring once stable SqlDelight ships AGP 9 support.
        // Tracking: cashapp/sqldelight#6139.
        mavenLocal {
            content {
                includeGroup("app.cash.sqldelight")
            }
        }
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            content {
                includeGroup("app.cash.sqldelight")
            }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("kotlinx") {
            from(files("gradle/kotlinx.versions.toml"))
        }
        create("androidx") {
            from(files("gradle/androidx.versions.toml"))
        }
        create("compose") {
            from(files("gradle/compose.versions.toml"))
        }
        create("aniyomilibs") {
            from(files("gradle/aniyomi.versions.toml"))
        }
    }
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Temporary for SqlDelight 2.3.0-SNAPSHOT AGP 9 support.
        // Remove this mavenLocal/snapshot wiring once stable SqlDelight ships AGP 9 support.
        // Tracking: cashapp/sqldelight#6139.
        mavenLocal {
            content {
                includeGroup("app.cash.sqldelight")
            }
        }
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/") {
            content {
                includeGroup("app.cash.sqldelight")
            }
        }
        mavenCentral()
        google()
        maven(url = "https://www.jitpack.io")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Rayniyomi"
include(":app")
include(":core-metadata")
include(":core:archive")
include(":core:common")
include(":data")
include(":domain")
include(":i18n")
include(":i18n-aniyomi")
include(":macrobenchmark")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")

include(":lightnovel-contract")
include(":lightnovel-plugin")
