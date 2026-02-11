package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadQueueMutationsTest {

    /**
     * Represents an entity that owns downloads (e.g., Episode, Chapter).
     * This matches the production type structure where Episode/Chapter are passed to removeFromQueue.
     */
    private data class FakeEntity(val id: Long, val name: String)

    /**
     * Represents a download item (e.g., AnimeDownload, MangaDownload).
     * Contains reference to the entity being downloaded.
     */
    private data class FakeDownload(val id: Long, val entity: FakeEntity)

    private fun createMutations(
        queueState: MutableStateFlow<List<FakeDownload>> = MutableStateFlow(emptyList()),
        createFromId: suspend (Long) -> FakeDownload? = { id ->
            FakeDownload(id, FakeEntity(id, "Entity $id"))
        },
        moveToFront: (FakeDownload) -> Unit = { },
        updateQueue: (List<FakeDownload>) -> Unit = { },
        addToStart: (List<FakeDownload>) -> Unit = { },
        removeFromQueue: (List<FakeEntity>) -> Unit = { entities ->
            // Match production behavior: filter downloads by entity ID
            queueState.value = queueState.value.filterNot { download ->
                entities.any { it.id == download.entity.id }
            }
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
        val entity1 = FakeEntity(1, "Entity 1")
        val entity2 = FakeEntity(2, "Entity 2")
        val queueState = MutableStateFlow(listOf(FakeDownload(1, entity1), FakeDownload(2, entity2)))
        val mutations = createMutations(queueState = queueState)

        assertEquals(FakeDownload(2, entity2), mutations.getQueuedDownloadOrNull(2))
        assertNull(mutations.getQueuedDownloadOrNull(99))
    }

    @Test
    fun `startDownloadNow uses existing download and starts`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val download1 = FakeDownload(1, entity1)
        val queueState = MutableStateFlow(listOf(download1))
        val moved = mutableListOf<FakeDownload>()
        var started = false

        val mutations = createMutations(
            queueState = queueState,
            moveToFront = { moved.add(it) },
            startDownloads = { started = true },
        )

        mutations.startDownloadNow(1)

        assertEquals(listOf(download1), moved)
        assertEquals(true, started)
    }

    @Test
    fun `startDownloadNow creates missing download`() = runTest {
        val moved = mutableListOf<FakeDownload>()
        val mutations = createMutations(
            createFromId = { id -> FakeDownload(id, FakeEntity(id, "Entity $id")) },
            moveToFront = { moved.add(it) },
        )

        mutations.startDownloadNow(5)

        assertEquals(1, moved.size)
        assertEquals(5, moved[0].id)
        assertEquals("Entity 5", moved[0].entity.name)
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
        val entity1 = FakeEntity(1, "Entity 1")
        val queueState = MutableStateFlow(listOf(FakeDownload(1, entity1)))
        var paused = false
        var stopped = false

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { true },
            pauseDownloader = { paused = true },
            stopDownloader = { stopped = true },
        )

        mutations.removeFromQueueSafely(listOf(entity1))

        assertEquals(true, paused)
        assertEquals(true, stopped)
        assertEquals(emptyList<FakeDownload>(), queueState.value)
    }

    @Test
    fun `removeFromQueueSafely restarts when queue remains`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val entity2 = FakeEntity(2, "Entity 2")
        val download2 = FakeDownload(2, entity2)
        val queueState = MutableStateFlow(listOf(FakeDownload(1, entity1), download2))
        var started = false

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { true },
            startDownloader = { started = true },
        )

        mutations.removeFromQueueSafely(listOf(entity1))

        assertEquals(true, started)
        assertEquals(listOf(download2), queueState.value)
    }

    @Test
    fun `removeFromQueueSafely handles concurrent calls safely`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val entity2 = FakeEntity(2, "Entity 2")
        val entity3 = FakeEntity(3, "Entity 3")
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, entity1),
                FakeDownload(2, entity2),
                FakeDownload(3, entity3),
            ),
        )
        var pauseCount = 0
        var startCount = 0

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { true },
            pauseDownloader = {
                pauseCount++
                Thread.sleep(10) // Simulate pause delay
            },
            startDownloader = {
                startCount++
            },
        )

        // Launch two concurrent removals
        val job1 = async { mutations.removeFromQueueSafely(listOf(entity1)) }
        val job2 = async {
            delay(5) // Slight delay to create contention
            mutations.removeFromQueueSafely(listOf(entity2))
        }

        job1.await()
        job2.await()

        // Both operations should complete
        assertEquals(2, pauseCount, "Should pause twice (once per removal)")
        assertEquals(2, startCount, "Should restart twice (once per removal)")
        // Only entity3 should remain
        assertEquals(1, queueState.value.size)
        assertEquals(entity3, queueState.value[0].entity)
    }

    @Test
    fun `removeFromQueueSafely handles callback errors gracefully`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val queueState = MutableStateFlow(listOf(FakeDownload(1, entity1)))
        var attemptedRestart = false

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { true },
            pauseDownloader = { throw RuntimeException("Pause failed") },
            removeFromQueue = {
                // Removal still happens despite pause failure
                queueState.value = emptyList()
            },
            stopDownloader = { attemptedRestart = true },
        )

        // Should not throw despite pause failure
        mutations.removeFromQueueSafely(listOf(entity1))

        // Removal should succeed and downloader should stop (queue empty)
        assertEquals(emptyList<FakeDownload>(), queueState.value)
        assertTrue(attemptedRestart, "Should attempt to stop after successful removal")
    }

    @Test
    fun `startDownloadNow handles createFromId failure gracefully`() = runTest {
        val mutations = createMutations(
            createFromId = { throw RuntimeException("Creation failed") },
        )

        // Should not throw, just silently return
        mutations.startDownloadNow(99)

        // No exception means test passes
    }
}
