package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

internal class DownloadCacheRenewal(private val scope: CoroutineScope) {

    private val lock = Any()
    private var state: State = State.LoadingDiskIndex(DeferredRenewal.None)

    /** Returns true when a caller asked for a renewal while the disk index loaded. */
    fun finishDiskIndexLoad(indexLoaded: Boolean): Boolean = synchronized(lock) {
        val loading = checkNotNull(state as? State.LoadingDiskIndex) { "The disk index already loaded" }
        val keepIndex = indexLoaded && loading.deferred != DeferredRenewal.Forced
        state = if (keepIndex) State.Fresh(System.currentTimeMillis()) else State.Stale
        loading.deferred != DeferredRenewal.None
    }

    /** Returns the started scan, or null when the cache is fresh or a scan already runs. */
    fun renewIfDue(scan: suspend CoroutineScope.(isFirstScan: Boolean) -> Unit): Job? {
        val job = synchronized(lock) {
            val isFirstScan = when (val current = state) {
                is State.LoadingDiskIndex -> {
                    state = current.copy(deferred = maxOf(current.deferred, DeferredRenewal.IfDue))
                    return null
                }
                is State.Scanning -> return null
                is State.Fresh -> if (current.isExpired()) false else return null
                State.Stale -> true
            }
            scope.launch(Dispatchers.IO, CoroutineStart.LAZY) { scan(isFirstScan) }
                .also { state = State.Scanning(it) }
        }
        job.invokeOnCompletion { finishScan(job) }
        job.start()
        return job
    }

    fun invalidate() {
        val cancelledScan = synchronized(lock) {
            val current = state
            state = if (current is State.LoadingDiskIndex) {
                current.copy(deferred = DeferredRenewal.Forced)
            } else {
                State.Stale
            }
            (current as? State.Scanning)?.job
        }
        cancelledScan?.cancel()
    }

    private fun finishScan(job: Job) = synchronized(lock) {
        val current = state
        if (current is State.Scanning && current.job === job) {
            state = State.Fresh(System.currentTimeMillis())
        }
    }

    private sealed interface State {
        data class LoadingDiskIndex(val deferred: DeferredRenewal) : State
        data class Scanning(val job: Job) : State
        data class Fresh(val renewedAt: Long) : State {
            fun isExpired() = System.currentTimeMillis() > renewedAt + RENEW_INTERVAL_MS
        }
        data object Stale : State
    }

    private enum class DeferredRenewal { None, IfDue, Forced }

    private companion object {
        val RENEW_INTERVAL_MS = 1.hours.inWholeMilliseconds
    }
}
