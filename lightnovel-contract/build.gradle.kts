plugins {
    id("mihon.library")
    kotlin("android")
}

android {
    namespace = "xyz.rayniyomi.lightnovel.contract"
}

dependencies {
    testImplementation(libs.bundles.test)
}
