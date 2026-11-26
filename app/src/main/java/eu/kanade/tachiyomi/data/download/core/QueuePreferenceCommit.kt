package eu.kanade.tachiyomi.data.download.core

import android.content.SharedPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

internal suspend fun commitQueuePreferences(editor: () -> SharedPreferences.Editor) {
    var retryDelay = 50L
    while (true) {
        currentCoroutineContext().ensureActive()
        if (editor().commit()) return
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
