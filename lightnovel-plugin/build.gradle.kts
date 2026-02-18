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
