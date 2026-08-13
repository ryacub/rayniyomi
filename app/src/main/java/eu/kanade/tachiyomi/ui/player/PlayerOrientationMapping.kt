package eu.kanade.tachiyomi.ui.player

import android.content.pm.ActivityInfo

/**
 * Maps an orientation preference to the matching Activity orientation constant.
 *
 * [videoAspect] is the video output aspect ratio. A ratio above 1.0 is landscape.
 * It is used only by [PlayerOrientation.Video].
 */
fun PlayerOrientation.toActivityOrientation(videoAspect: Double?): Int = when (this) {
    PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
    PlayerOrientation.Video -> if ((videoAspect ?: 0.0) > 1.0) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
    PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}
