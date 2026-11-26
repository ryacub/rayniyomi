package eu.kanade.tachiyomi.data.download.core

import android.content.SharedPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.IOException

internal suspend fun commitQueuePreferences(
    maxAttempts: Int? = null,
    editor: () -> SharedPreferences.Editor,
): Boolean {
    var retryDelay = 50L
    var failures = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        if (editor().commit()) return true
        if (maxAttempts != null && ++failures >= maxAttempts) return false
        delay(retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(1_000)
    }
}

internal fun Any.writeQueueRollbackMarker(editor: () -> SharedPreferences.Editor) {
    try {
        if (editor().commit()) return
        logcat(LogPriority.WARN) { "Could not write the download queue rollback marker" }
    } catch (e: RuntimeException) {
        logcat(LogPriority.WARN, e) { "Could not write the download queue rollback marker" }
    }
}

internal class DownloadQueueWriteException : IOException("Could not write the download queue")
