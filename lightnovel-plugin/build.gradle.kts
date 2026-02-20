plugins {
    id("mihon.android.application")
    kotlin("plugin.serialization")
}

android {
    namespace = "xyz.rayniyomi.plugin.lightnovel"

    defaultConfig {
        applicationId = "xyz.rayniyomi.plugin.lightnovel"
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("PLUGIN_KEYSTORE_PATH") ?: "/dev/null")
            storePassword = System.getenv("PLUGIN_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("PLUGIN_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("PLUGIN_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = if (System.getenv("PLUGIN_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(androidx.appcompat)
    implementation(androidx.corektx)
    implementation(androidx.bundles.lifecycle)

    implementation(libs.jsoup)
    implementation(kotlinx.serialization.json)
    implementation(kotlinx.coroutines.android)

    testImplementation(libs.bundles.test)
}
