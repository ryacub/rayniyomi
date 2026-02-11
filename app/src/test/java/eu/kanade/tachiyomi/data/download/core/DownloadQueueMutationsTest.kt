package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadQueueMutationsTest {

    private data class FakeDownload(val id: Long)

    private fun createMutations(
        queueState: MutableStateFlow<List<FakeDownload>> = MutableStateFlow(emptyList()),
        createFromId: suspend (Long) -> FakeDownload? = { id -> FakeDownload(id) },
        moveToFront: (FakeDownload) -> Unit = { },
        updateQueue: (List<FakeDownload>) -> Unit = { },
        addToStart: (List<FakeDownload>) -> Unit = { },
        removeFromQueue: (List<Long>) -> Unit = { ids ->
            queueState.value = queueState.value.filterNot { it.id in ids }
        },
        isRunning: () -> Boolean = { false },
        pauseDownloader: () -> Unit = { },
        startDownloader: () -> Unit = { },
        stopDownloader: () -> Unit = { },
        startDownloads: () -> Unit = { },
    ): DownloadQueueMutations<FakeDownload, Long> {
        return DownloadQueueMutations(
            queueState = queueState,
            itemId = { it.id },
            createFromId = createFromId,
            moveToFront = moveToFront,
            updateQueue = updateQueue,
            addToStart = addToStart,
            removeFromQueue = removeFromQueue,
            isRunning = isRunning,
            pauseDownloader = pauseDownloader,
            startDownloader = startDownloader,
            stopDownloader = stopDownloader,
            startDownloads = startDownloads,
            queueMutex = Mutex(),
        )
    }

    @Test
    fun `getQueuedDownloadOrNull returns matching download`() {
        val queueState = MutableStateFlow(listOf(FakeDownload(1), FakeDownload(2)))
        val mutations = createMutations(queueState = queueState)

        assertEquals(FakeDownload(2), mutations.getQueuedDownloadOrNull(2))
        assertNull(mutations.getQueuedDownloadOrNull(99))
    }

    @Test
    fun `startDownloadNow uses existing download and starts`() = runTest {
        val queueState = MutableStateFlow(listOf(FakeDownload(1)))
        val moved = mutableListOf<FakeDownload>()
        var started = false

        val mutations = createMutations(
            queueState = queueState,
            moveToFront = { moved.add(it) },
            startDownloads = { started = true },
        )

        mutations.startDownloadNow(1)

        assertEquals(listOf(FakeDownload(1)), moved)
        assertEquals(true, started)
    }

    @Test
    fun `startDownloadNow creates missing download`() = runTest {
        val moved = mutableListOf<FakeDownload>()
        val mutations = createMutations(
            createFromId = { id -> FakeDownload(id) },
            moveToFront = { moved.add(it) },
        )

        mutations.startDownloadNow(5)

        assertEquals(listOf(FakeDownload(5)), moved)
    }

    @Test
    fun `addDownloadsToStart skips empty list`() {
        var started = false
        var added = false
        val mutations = createMutations(
            addToStart = { added = true },
        )

        mutations.addDownloadsToStart(emptyList()) { started = true }

        assertEquals(false, added)
        assertEquals(false, started)
    }

    @Test
    fun `removeFromQueueSafely stops when queue empties`() = runTest {
        val queueState = MutableStateFlow(listOf(FakeDownload(1)))
        var paused = false
        var stopped = false

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { true },
            pauseDownloader = { paused = true },
            stopDownloader = { stopped = true },
        )

        mutations.removeFromQueueSafely(listOf(1))

        assertEquals(true, paused)
        assertEquals(true, stopped)
        assertEquals(emptyList<FakeDownload>(), queueState.value)
    }

    @Test
    fun `removeFromQueueSafely restarts when queue remains`() = runTest {
        val queueState = MutableStateFlow(listOf(FakeDownload(1), FakeDownload(2)))
        var started = false

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { true },
            startDownloader = { started = true },
        )

        mutations.removeFromQueueSafely(listOf(1))

        assertEquals(true, started)
        assertEquals(listOf(FakeDownload(2)), queueState.value)
    }
}
