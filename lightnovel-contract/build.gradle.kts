plugins {
    id("mihon.library")
}

android {
    namespace = "xyz.rayniyomi.lightnovel.contract"
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test)
}
