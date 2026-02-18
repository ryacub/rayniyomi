plugins {
    id("mihon.android.application")
    id("mihon.android.application.compose")
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
}

dependencies {
    implementation(androidx.appcompat)
    implementation(androidx.corektx)
    implementation(androidx.bundles.lifecycle)

    implementation(platform(compose.bom))
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    implementation(libs.jsoup)
    implementation(kotlinx.serialization.json)
    implementation(kotlinx.coroutines.android)

    testImplementation(libs.bundles.test)

    androidTestImplementation(platform(compose.bom))
    androidTestImplementation(compose.ui.test.junit4)
    androidTestImplementation(androidx.test.ext)
    androidTestImplementation(androidx.test.espresso.core)
    debugImplementation(compose.ui.test.manifest)
}
