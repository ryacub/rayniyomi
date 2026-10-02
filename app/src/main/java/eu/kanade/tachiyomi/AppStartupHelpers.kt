package eu.kanade.tachiyomi

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.work.Configuration
import eu.kanade.domain.track.service.PeriodicTrackerSyncJob
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.util.system.CrashlyticLogger
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import tachiyomi.presentation.widget.entries.anime.AnimeWidgetManager
import tachiyomi.presentation.widget.entries.manga.MangaWidgetManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val TAG = "AppStartupHelpers"

internal fun installLogcatLoggerIfEnabled(networkPreferences: NetworkPreferences) {
    if (!LogcatLogger.isInstalled && networkPreferences.verboseLogging().get()) {
        LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
    }
}

internal fun setupWidgetManagers(context: Context, lifecycleScope: LifecycleCoroutineScope) {
    try {
        with(MangaWidgetManager(Injekt.get(), Injekt.get())) {
            context.init(lifecycleScope)
        }
    } catch (e: Exception) {
        Log.e(TAG, "MangaWidgetManager initialization failed", e)
    }
    try {
        with(AnimeWidgetManager(Injekt.get(), Injekt.get())) {
            context.init(lifecycleScope)
        }
    } catch (e: Exception) {
        Log.e(TAG, "AnimeWidgetManager initialization failed", e)
    }
}

internal fun schedulePeriodicTrackerSync(context: Context) {
    try {
        PeriodicTrackerSyncJob.setupTask(context)
    } catch (e: Exception) {
        Log.e(TAG, "PeriodicTrackerSyncJob.setupTask failed", e)
    }
}

internal fun buildWorkManagerConfiguration(
    record: (Exception, String) -> Unit = CrashlyticLogger::logException,
): Configuration = Configuration.Builder()
    .setWorkerInitializationExceptionHandler { info ->
        val exception = Exception(
            "Worker initialization failed for ${info.workerClassName}",
            info.throwable,
        )
        record(exception, "worker_initialization")
    }
    .build()
