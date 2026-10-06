package eu.kanade.tachiyomi.ui.entries.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

internal class InFlightRefreshes<T>(private val scope: CoroutineScope) {

    private val running = HashMap<Long, Deferred<T>>()

    @Synchronized
    fun join(entryId: Long, refresh: suspend () -> T): Deferred<T> {
        running[entryId]?.let { return it }
        val deferred = scope.async(start = CoroutineStart.LAZY) { refresh() }
        running[entryId] = deferred
        deferred.invokeOnCompletion { forget(entryId, deferred) }
        deferred.start()
        return deferred
    }

    @Synchronized
    private fun forget(entryId: Long, deferred: Deferred<T>) {
        if (running[entryId] === deferred) running.remove(entryId)
    }
}
