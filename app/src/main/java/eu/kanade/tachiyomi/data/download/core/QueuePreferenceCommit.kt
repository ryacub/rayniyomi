package eu.kanade.tachiyomi.data.download.core

import android.content.SharedPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal suspend fun commitQueuePreferences(editor: () -> SharedPreferences.Editor) {
    var retryDelay = 50L
    while (true) {
        currentCoroutineContext().ensureActive()
        if (editor().commit()) return
        delay(retryDelay)
        retryDelay = (retryDelay * 2).coerceAtMost(1_000)
    }
}
