package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadQueueMutationsTest {

    private data class FakeEntity(val id: Long)
    private data class FakeDownload(val id: Long, val entity: FakeEntity)

    private fun createMutations(
        mutationScope: CoroutineScope,
        queueState: MutableStateFlow<List<FakeDownload>> = MutableStateFlow(emptyList()),
        createFromId: suspend (Long) -> FakeDownload? = { id -> FakeDownload(id, FakeEntity(id)) },
        moveToFront: (FakeDownload) -> Unit = { },
        updateQueue: (List<FakeDownload>) -> Unit = { },
        addToStart: (List<FakeDownload>) -> Unit = { },
        removeFromQueueByIds: (List<Long>) -> Unit = { ids ->
            val set = ids.toSet()
            queueState.value = queueState.value.filterNot { it.entity.id in set }
        },
        isRunning: () -> Boolean = { false },
        pauseDownloader: () -> Unit = { },
        startDownloader: () -> Unit = { },
        stopDownloader: () -> Unit = { },
        startDownloads: () -> Unit = { },
    ): DownloadQueueMutations<FakeDownload, FakeEntity> {
        return DownloadQueueMutations(
            queueState = queueState,
            itemId = { it.id },
            ownerItemId = { it.id },
            createFromId = createFromId,
            moveToFront = moveToFront,
            updateQueue = updateQueue,
            addToStart = addToStart,
            removeFromQueueByIds = removeFromQueueByIds,
            isRunning = isRunning,
            pauseDownloader = pauseDownloader,
            startDownloader = startDownloader,
            stopDownloader = stopDownloader,
            startDownloads = startDownloads,
            mutationScope = mutationScope,
        )
    }

    @Test
    fun `getQueuedDownloadOrNull returns matching download`() = runTest {
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, FakeEntity(1)),
                FakeDownload(2, FakeEntity(2)),
            ),
        )
        val mutations = createMutations(backgroundScope, queueState = queueState)

        assertEquals(2L, mutations.getQueuedDownloadOrNull(2)?.id)
        assertNull(mutations.getQueuedDownloadOrNull(99))
    }

    @Test
    fun `startDownloadNow moves existing item to front and starts downloads`() = runTest {
        val queueState = MutableStateFlow(listOf(FakeDownload(1, FakeEntity(1))))
        val moved = mutableListOf<FakeDownload>()
        var started = false

        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            moveToFront = { moved.add(it) },
            startDownloads = { started = true },
        )

        mutations.startDownloadNow(1)

        assertEquals(listOf(1L), moved.map { it.id })
        assertTrue(started)
    }

    @Test
    fun `reorderQueue normalizes stale order against live queue`() = runTest {
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, FakeEntity(1)),
                FakeDownload(2, FakeEntity(2)),
                FakeDownload(3, FakeEntity(3)),
            ),
        )
        val reordered = mutableListOf<List<FakeDownload>>()
        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            updateQueue = {
                reordered += it
                queueState.value = it
            },
        )

        // Includes stale ID 99 and omits ID 2; reducer must normalize.
        mutations.reorderQueueByIds(listOf(3, 99, 1))

        assertEquals(listOf(3L, 1L, 2L), reordered.single().map { it.id })
    }

    @Test
    fun `addDownloadsToStartByIds adds only new ids and triggers start callback`() = runTest {
        val queueState = MutableStateFlow(listOf(FakeDownload(1, FakeEntity(1))))
        var started = false

        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            createFromId = { id -> FakeDownload(id, FakeEntity(id)) },
            addToStart = { downloads ->
                val existing = queueState.value.map { it.id }.toSet()
                val newItems = downloads.filterNot { it.id in existing }
                queueState.value = newItems + queueState.value
            },
        )

        mutations.addDownloadsToStartByIds(listOf(2, 1)) { started = true }

        assertEquals(listOf(2L, 1L), queueState.value.map { it.id })
        assertTrue(started)
    }

    @Test
    fun `removeFromQueueSafely stops when queue empties`() = runTest {
        val queueState = MutableStateFlow(listOf(FakeDownload(1, FakeEntity(1))))
        var paused = false
        var stopped = false

        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            isRunning = { true },
            pauseDownloader = { paused = true },
            stopDownloader = { stopped = true },
        )

        mutations.removeFromQueueSafelyByItemIds(listOf(1))

        assertTrue(paused)
        assertTrue(stopped)
        assertTrue(queueState.value.isEmpty())
    }

    @Test
    fun `removeFromQueueSafely starts when queue remains`() = runTest {
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, FakeEntity(1)),
                FakeDownload(2, FakeEntity(2)),
            ),
        )
        var started = false

        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            isRunning = { true },
            startDownloader = { started = true },
        )

        mutations.removeFromQueueSafelyByItemIds(listOf(1))

        assertTrue(started)
        assertEquals(listOf(2L), queueState.value.map { it.id })
    }

    @Test
    fun `concurrent remove and reorder does not resurrect removed id`() = runTest {
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, FakeEntity(1)),
                FakeDownload(2, FakeEntity(2)),
                FakeDownload(3, FakeEntity(3)),
            ),
        )

        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            updateQueue = { queueState.value = it },
            removeFromQueueByIds = { ids ->
                val set = ids.toSet()
                queueState.value = queueState.value.filterNot { it.entity.id in set }
            },
            isRunning = { true },
        )

        val remove = async { mutations.removeFromQueueSafelyByItemIds(listOf(2)) }
        val reorder = async { mutations.reorderQueueByIds(listOf(3, 2, 1)) }
        remove.await()
        reorder.await()

        assertFalse(queueState.value.any { it.id == 2L })
    }

    @Test
    fun `command failure does not kill actor and later command still runs`() = runTest {
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, FakeEntity(1)),
                FakeDownload(2, FakeEntity(2)),
            ),
        )

        var failOnce = true
        val mutations = createMutations(
            mutationScope = backgroundScope,
            queueState = queueState,
            updateQueue = { reordered ->
                if (failOnce) {
                    failOnce = false
                    throw IllegalStateException("boom")
                }
                queueState.value = reordered
            },
        )

        val failed = withTimeoutOrNull(500) {
            try {
                mutations.reorderQueueByIds(listOf(2, 1))
                false
            } catch (_: IllegalStateException) {
                true
            }
        }
        assertTrue(failed == true)

        mutations.reorderQueueByIds(listOf(2, 1))
        assertEquals(listOf(2L, 1L), queueState.value.map { it.id })
    }

    @Test
    fun `startDownloadNow returns without start when createFromId is null`() = runTest {
        var moved = false
        var started = false
        val mutations = createMutations(
            mutationScope = backgroundScope,
            createFromId = { null },
            moveToFront = { moved = true },
            startDownloads = { started = true },
        )

        mutations.startDownloadNow(9)

        assertFalse(moved)
        assertFalse(started)
    }
}
