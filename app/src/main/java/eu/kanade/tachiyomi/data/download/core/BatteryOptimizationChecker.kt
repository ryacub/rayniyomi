package eu.kanade.tachiyomi.data.download.core

import android.content.Context
import android.os.PowerManager

class BatteryOptimizationChecker(
    private val context: Context,
    private val powerManager: PowerManager?,
) {
    fun isOptimizationEnabled(): Boolean {
        if (powerManager == null) return true
        return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
