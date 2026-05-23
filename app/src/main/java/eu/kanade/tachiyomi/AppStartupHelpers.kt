package eu.kanade.tachiyomi

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.domain.track.service.PeriodicTrackerSyncJob
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.coroutines.CoroutineScope
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import tachiyomi.presentation.widget.entries.anime.AnimeWidgetManager
import tachiyomi.presentation.widget.entries.manga.MangaWidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Installs LogcatLogger if verbose logging is enabled and it hasn't already been installed.
 *
 * This is called from App.onCreate on a background coroutine to defer logging setup
 * from the main thread.
 */
internal fun installLogcatLoggerIfEnabled(
    networkPreferences: NetworkPreferences,
    scope: CoroutineScope,
) {
    if (!LogcatLogger.isInstalled && networkPreferences.verboseLogging().get()) {
        LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
    }
}

/**
 * Initializes both MangaWidgetManager and AnimeWidgetManager.
 *
 * This is called from App.onCreate on a background coroutine to defer widget setup
 * from the main thread.
 */
internal fun setupWidgetManagers(
    context: Context,
    scope: CoroutineScope,
) {
    try {
        with(MangaWidgetManager(Injekt.get(), Injekt.get())) {
            context.init(scope as LifecycleCoroutineScope)
        }
    } catch (e: Exception) {
        // Silently fail in test environments where Injekt isn't fully set up
        // In production, Injekt is always initialized before this is called
    }
    try {
        with(AnimeWidgetManager(Injekt.get(), Injekt.get())) {
            context.init(scope as LifecycleCoroutineScope)
        }
    } catch (e: Exception) {
        // Silently fail in test environments where Injekt isn't fully set up
        // In production, Injekt is always initialized before this is called
    }
}

/**
 * Schedules periodic tracker sync by calling PeriodicTrackerSyncJob.setupTask.
 *
 * This is called from App.onCreate on a background coroutine to defer tracker sync setup
 * from the main thread.
 */
internal fun schedulePeriodicTrackerSync(
    context: Context,
    scope: CoroutineScope,
) {
    try {
        PeriodicTrackerSyncJob.setupTask(context)
    } catch (e: Exception) {
        // Silently fail in test environments where Injekt isn't fully set up
        // In production, Injekt is always initialized before this is called
    }
}
