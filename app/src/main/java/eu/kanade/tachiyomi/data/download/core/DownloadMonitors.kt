package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs endless progress and stall monitors beside a download.
 */
object DownloadMonitors {

    /**
     * Runs [block] while every monitor in [monitors] runs, then stops each monitor.
     *
     * A monitor collects a state flow or loops on a status field, so it never ends on its own.
     * `coroutineScope` returns only after every child job ends, so the cancellation must happen
     * inside the same scope that started the monitors. A `finally` block placed after the scope
     * never runs, because the scope waits for the monitors it is meant to stop.
     *
     * Monitors are cancelled in reverse list order: the last-listed monitor is cancelled first.
     * Pass monitors in start order (e.g., progress monitor then stall monitor) so they stop in
     * the desired order (stall stops first, then progress).
     */
    suspend fun <T> withMonitors(
        monitors: List<suspend () -> Unit>,
        block: suspend () -> T,
    ): T = coroutineScope {
        val jobs = monitors.map { monitor -> launch { monitor() } }
        try {
            block()
        } finally {
            withContext(NonCancellable) {
                jobs.asReversed().forEach { job ->
                    job.cancel()
                    job.join()
                }
            }
        }
    }
}
