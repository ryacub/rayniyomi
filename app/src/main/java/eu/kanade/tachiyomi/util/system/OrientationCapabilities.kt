package eu.kanade.tachiyomi.util.system

import android.content.res.Configuration
import android.os.Build

private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

// Assumes the Android 16 rule for apps targeting API 36 without the
// PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY opt-out: the platform ignores
// orientation requests on displays with smallestScreenWidthDp >= 600. The rule
// also holds for API 37+, so sdkInt >= BAKLAVA covers later APIs. Revisit this
// if targetSdk or the opt-out changes.
fun honorsOrientationRequests(sdkInt: Int, smallestScreenWidthDp: Int): Boolean =
    sdkInt < Build.VERSION_CODES.BAKLAVA || smallestScreenWidthDp < LARGE_SCREEN_SMALLEST_WIDTH_DP

fun honorsOrientationRequests(configuration: Configuration): Boolean =
    honorsOrientationRequests(Build.VERSION.SDK_INT, configuration.smallestScreenWidthDp)
