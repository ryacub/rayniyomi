package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
    fun `addDownloadsToStart skips empty list`() = runTest {
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
            },
            startDownloader = {
                startCount++
            },
        )

        // Launch two concurrent removals
        val job1 = async { mutations.removeFromQueueSafely(listOf(entity1)) }
        val job2 = async { mutations.removeFromQueueSafely(listOf(entity2)) }

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
    fun `startDownloadNow does not reinsert item removed concurrently`() = runTest {
        val target = FakeEntity(7, "Entity 7")
        val queueState = MutableStateFlow<List<FakeDownload>>(emptyList())
        val createStarted = CompletableDeferred<Unit>()
        val allowCreate = CompletableDeferred<Unit>()
        val removeCompleted = CompletableDeferred<Unit>()

        val mutations = createMutations(
            queueState = queueState,
            createFromId = { id ->
                createStarted.complete(Unit)
                allowCreate.await()
                FakeDownload(id, target)
            },
            moveToFront = { download ->
                queueState.value = listOf(download) + queueState.value.filterNot { it.id == download.id }
            },
            removeFromQueue = { entities ->
                queueState.value = queueState.value.filterNot { download ->
                    entities.any { it.id == download.entity.id }
                }
                removeCompleted.complete(Unit)
            },
        )

        val startNowJob = async { mutations.startDownloadNow(target.id) }
        createStarted.await()

        val removeJob = async {
            mutations.removeFromQueueSafely(listOf(target))
        }

        // If startDownloadNow is unlocked, removal can finish before creation resumes.
        val removeFinishedEarly = withTimeoutOrNull(50) { removeCompleted.await() }
        assertNull(removeFinishedEarly, "Removal should be blocked while startDownloadNow holds queueMutex")
        allowCreate.complete(Unit)

        startNowJob.await()
        removeJob.await()

        val targetStillQueued = queueState.value.any { it.entity.id == target.id }
        assertEquals(false, targetStillQueued, "Cancelled target should not be reinserted")
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
    fun `removeFromQueueSafely skips pause and restart when not running`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val queueState = MutableStateFlow(listOf(FakeDownload(1, entity1)))
        var paused = false
        var started = false
        var stopped = false

        val mutations = createMutations(
            queueState = queueState,
            isRunning = { false },
            pauseDownloader = { paused = true },
            startDownloader = { started = true },
            stopDownloader = { stopped = true },
        )

        mutations.removeFromQueueSafely(listOf(entity1))

        assertEquals(false, paused, "Should not pause when not running")
        assertEquals(false, started, "Should not start when not running")
        assertEquals(false, stopped, "Should not stop when not running")
        assertEquals(emptyList<FakeDownload>(), queueState.value)
    }

    @Test
    fun `reorderQueue delegates to updateQueue`() = runTest {
        val reordered = mutableListOf<List<FakeDownload>>()
        val queueState = MutableStateFlow(
            listOf(
                FakeDownload(1, FakeEntity(1, "Entity 1")),
                FakeDownload(2, FakeEntity(2, "Entity 2")),
                FakeDownload(3, FakeEntity(3, "Entity 3")),
            ),
        )
        val mutations = createMutations(
            queueState = queueState,
            updateQueue = { reordered.add(it) },
        )
        val newOrder = listOf(
            FakeDownload(3, FakeEntity(3, "Entity 3")),
            FakeDownload(1, FakeEntity(1, "Entity 1")),
        )

        mutations.reorderQueue(newOrder)

        assertEquals(1, reordered.size)
        assertEquals(listOf(3L, 1L, 2L), reordered[0].map { it.id })
    }

    @Test
    fun `addDownloadsToStart calls addToStart and startIfNeeded`() = runTest {
        val added = mutableListOf<List<FakeDownload>>()
        var started = false
        val mutations = createMutations(
            addToStart = { added.add(it) },
        )
        val downloads = listOf(FakeDownload(1, FakeEntity(1, "Entity 1")))

        mutations.addDownloadsToStart(downloads) { started = true }

        assertEquals(1, added.size)
        assertEquals(downloads, added[0])
        assertTrue(started, "startIfNeeded should be called")
    }

    @Test
    fun `reorderQueue does not reintroduce removed item during concurrent removal`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val entity2 = FakeEntity(2, "Entity 2")
        val entity3 = FakeEntity(3, "Entity 3")
        val download1 = FakeDownload(1, entity1)
        val download2 = FakeDownload(2, entity2)
        val download3 = FakeDownload(3, entity3)
        val queueState = MutableStateFlow(listOf(download1, download2, download3))

        val removeApplied = CompletableDeferred<Unit>()

        val mutations = createMutations(
            queueState = queueState,
            updateQueue = { reordered -> queueState.value = reordered },
            isRunning = { true },
            removeFromQueue = { entities ->
                queueState.value = queueState.value.filterNot { download ->
                    entities.any { it.id == download.entity.id }
                }
                removeApplied.complete(Unit)
            },
        )

        val removeJob = async { mutations.removeFromQueueSafely(listOf(entity2)) }
        val reorderJob = async {
            removeApplied.await()
            mutations.reorderQueue(listOf(download3, download1, download2))
        }

        removeJob.await()
        reorderJob.await()

        assertEquals(
            listOf(1L, 3L),
            queueState.value.map { it.entity.id }.sorted(),
            "Removed item should not be reintroduced by concurrent reorder",
        )
    }

    @Test
    fun `addDownloadsToStart preserves remove outcome during concurrent removal`() = runTest {
        val entity1 = FakeEntity(1, "Entity 1")
        val entity2 = FakeEntity(2, "Entity 2")
        val entity3 = FakeEntity(3, "Entity 3")
        val download1 = FakeDownload(1, entity1)
        val download2 = FakeDownload(2, entity2)
        val download3 = FakeDownload(3, entity3)
        val queueState = MutableStateFlow(listOf(download1, download2, download3))

        val removeApplied = CompletableDeferred<Unit>()

        val mutations = createMutations(
            queueState = queueState,
            addToStart = { downloads ->
                val existingIds = queueState.value.map { it.id }.toSet()
                val newItems = downloads.filterNot { it.id in existingIds }
                queueState.value = newItems + queueState.value
            },
            isRunning = { true },
            removeFromQueue = { entities ->
                queueState.value = queueState.value.filterNot { download ->
                    entities.any { it.id == download.entity.id }
                }
                removeApplied.complete(Unit)
            },
        )

        val removeJob = async { mutations.removeFromQueueSafely(listOf(entity2)) }
        val addToStartJob = async {
            removeApplied.await()
            mutations.addDownloadsToStart(listOf(download3)) { }
        }

        removeJob.await()
        addToStartJob.await()

        assertEquals(
            listOf(1L, 3L),
            queueState.value.map { it.entity.id }.sorted(),
            "Removed item should remain absent after concurrent add-to-start operation",
        )
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

    @Test
    fun `startDownloadNow does nothing when createFromId returns null`() = runTest {
        var moved = false
        var started = false
        val mutations = createMutations(
            createFromId = { null },
            moveToFront = { moved = true },
            startDownloads = { started = true },
        )

        mutations.startDownloadNow(99)

        assertEquals(false, moved, "Should not move when createFromId returns null")
        assertEquals(false, started, "Should not start when createFromId returns null")
    }
}
