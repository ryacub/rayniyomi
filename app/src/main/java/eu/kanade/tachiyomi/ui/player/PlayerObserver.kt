package eu.kanade.tachiyomi.ui.player

import android.widget.Toast
import eu.kanade.tachiyomi.util.system.toast
import `is`.xyz.mpv.MPVLib
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class PlayerObserver(val activity: PlayerActivity) :
    MPVLib.EventObserver,
    MPVLib.LogObserver {

    override fun eventProperty(property: String) {
        activity.runOnUiThread { activity.onObserverEvent(property) }
    }

    override fun eventProperty(property: String, value: Long) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun eventProperty(property: String, value: Boolean) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun eventProperty(property: String, value: String) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun eventProperty(property: String, value: Double) {
        activity.runOnUiThread { activity.onObserverEvent(property, value) }
    }

    override fun event(eventId: Int) {
        // Track on mpv's event thread so the tracker sees events in the order mpv sent them.
        when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_START_FILE -> activity.mpvLoadTracker.onFileStarted()
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> activity.mpvLoadTracker.onFileLoaded()
        }
        activity.runOnUiThread { activity.event(eventId) }
    }

    override fun efEvent(err: String?) {
        var errorMessage = err ?: "Error: File ended"
        if (!httpError.isNullOrEmpty()) {
            errorMessage += ": $httpError"
            httpError = null
        }
        logcat(LogPriority.ERROR) { errorMessage }
        val failedVideo = activity.mpvLoadTracker.onLoadError()
        activity.runOnUiThread {
            activity.toast(errorMessage, Toast.LENGTH_LONG)
            failedVideo?.let(activity::onVideoLoadFailed)
        }
    }

    private var httpError: String? = null

    override fun logMessage(prefix: String, level: Int, text: String) {
        val logPriority = when (level) {
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_FATAL, MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> LogPriority.ERROR
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_WARN -> LogPriority.WARN
            MPVLib.mpvLogLevel.MPV_LOG_LEVEL_INFO -> LogPriority.INFO
            else -> LogPriority.VERBOSE
        }
        if (text.contains("HTTP error")) httpError = text
        logcat.logcat("mpv/$prefix", logPriority) { text }
    }
}
