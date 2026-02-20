package eu.kanade.tachiyomi.feature.novel

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Wraps all host-side Intent-based launches of the light novel plugin.
 *
 * Every entry point first checks [LightNovelFeatureGate.isFeatureAvailable] so that:
 * - the feature is gated by user preference AND plugin readiness, and
 * - any launch attempt while the plugin is absent/invalid is a silent no-op rather than a crash.
 *
 * @param activityStarter Injectable function that accepts an [Intent] and starts the target
 *   activity. Defaults to [Context.startActivity]. Provided so unit tests can substitute a
 *   test double without relying on the un-mocked Android framework.
 * @param libraryIntentFactory Factory that produces the [Intent] used to launch the plugin's
 *   library screen. Defaults to building an explicit Intent targeting the plugin's MainActivity.
 *   Provided for unit-test substitution.
 */
class LightNovelPluginLauncher(
    private val context: Context,
    private val featureGate: LightNovelFeatureGate,
    private val activityStarter: (Intent) -> Unit = context::startActivity,
    private val libraryIntentFactory: () -> Intent = {
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
                "${LightNovelPluginManager.PLUGIN_PACKAGE_NAME}.MainActivity",
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    },
) {
    /**
     * Returns `true` when the feature toggle is enabled AND the plugin is installed,
     * trusted, and compatible with this host version.
     */
    fun isAvailable(): Boolean = featureGate.isFeatureAvailable()

    /**
     * Launches the plugin's library/browse screen.
     *
     * @return `true` if the activity was started successfully, `false` otherwise (gate off,
     *   plugin not ready, [ActivityNotFoundException], or [SecurityException]).
     */
    fun launchLibrary(): Boolean {
        if (!isAvailable()) return false
        return tryLaunchActivity(libraryIntentFactory())
    }

    private fun tryLaunchActivity(intent: Intent): Boolean {
        return try {
            activityStarter(intent)
            true
        } catch (e: ActivityNotFoundException) {
            logcat(LogPriority.WARN) { "Light novel plugin not found: ${e.message}" }
            false
        } catch (e: SecurityException) {
            logcat(LogPriority.ERROR) { "Light novel plugin launch blocked: ${e.message}" }
            false
        }
    }
}
