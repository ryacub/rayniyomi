package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-writer actor for queue mutations.
 *
 * Command handlers execute strictly in submission order.
 */
class DownloadQueueActor(
    @Suppress("UNUSED_PARAMETER")
    scope: CoroutineScope,
) {
    private val queueLock = Mutex()

    suspend fun <T> submit(
        command: DownloadQueueCommand,
        operation: suspend () -> T,
    ): T {
        val result = CompletableDeferred<T>()
        queueLock.withLock {
            Envelope(command, operation, result).execute()
        }
        return result.await()
    }

    private class Envelope<T>(
        private val command: DownloadQueueCommand,
        private val operation: suspend () -> T,
        private val result: CompletableDeferred<T>,
    ) {
        suspend fun execute() {
            try {
                result.complete(operation())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
    }
}
