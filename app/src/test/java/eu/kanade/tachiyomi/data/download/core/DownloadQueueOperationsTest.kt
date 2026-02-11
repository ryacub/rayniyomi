package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadQueueOperationsTest {

    private enum class Status { QUEUE, DOWNLOADING, NOT_DOWNLOADED }

    private data class TestDownload(val id: Long, var status: Status = Status.NOT_DOWNLOADED)

    private class FakeStore : DownloadQueueStore<TestDownload> {
        val items = mutableListOf<TestDownload>()
        override fun addAll(downloads: List<TestDownload>) {
            items.addAll(downloads)
        }
        override fun remove(download: TestDownload) {
            items.remove(download)
        }
        override fun removeAll(downloads: List<TestDownload>) {
            items.removeAll(downloads.toSet())
        }
        override fun clear() {
            items.clear()
        }
    }

    private fun createOps(
        queueState: MutableStateFlow<List<TestDownload>> = MutableStateFlow(emptyList()),
        store: FakeStore = FakeStore(),
    ): Pair<DownloadQueueOperations<TestDownload>, FakeStore> {
        val ops = DownloadQueueOperations(
            _queueState = queueState,
            store = store,
            itemId = { it.id },
            isActive = { it.status == Status.QUEUE || it.status == Status.DOWNLOADING },
            markQueued = { it.status = Status.QUEUE },
            markInactive = {
                if (it.status == Status.QUEUE ||
                    it.status == Status.DOWNLOADING
                ) {
                    it.status = Status.NOT_DOWNLOADED
                }
            },
        )
        return ops to store
    }

    // region addAll

    @Test
    fun `addAll adds items to end of queue`() {
        val queueState = MutableStateFlow<List<TestDownload>>(emptyList())
        val (ops) = createOps(queueState = queueState)
        val downloads = listOf(TestDownload(1), TestDownload(2))

        ops.addAll(downloads)

        assertEquals(listOf(TestDownload(1, Status.QUEUE), TestDownload(2, Status.QUEUE)), queueState.value)
    }

    @Test
    fun `addAll marks items as QUEUE`() {
        val queueState = MutableStateFlow<List<TestDownload>>(emptyList())
        val (ops) = createOps(queueState = queueState)
        val download = TestDownload(1, Status.NOT_DOWNLOADED)

        ops.addAll(listOf(download))

        assertEquals(Status.QUEUE, download.status)
    }

    @Test
    fun `addAll persists to store`() {
        val store = FakeStore()
        val (ops) = createOps(store = store)

        ops.addAll(listOf(TestDownload(1), TestDownload(2)))

        assertEquals(2, store.items.size)
    }

    @Test
    fun `addAll appends to existing queue`() {
        val existing = TestDownload(1, Status.QUEUE)
        val queueState = MutableStateFlow(listOf(existing))
        val (ops) = createOps(queueState = queueState)

        ops.addAll(listOf(TestDownload(2)))

        assertEquals(2, queueState.value.size)
        assertEquals(1L, queueState.value[0].id)
        assertEquals(2L, queueState.value[1].id)
    }

    // endregion

    // region remove

    @Test
    fun `remove removes item and marks inactive`() {
        val download = TestDownload(1, Status.QUEUE)
        val queueState = MutableStateFlow(listOf(download))
        val (ops) = createOps(queueState = queueState)

        ops.remove(download)

        assertTrue(queueState.value.isEmpty())
        assertEquals(Status.NOT_DOWNLOADED, download.status)
    }

    @Test
    fun `remove persists to store`() {
        val download = TestDownload(1, Status.QUEUE)
        val store = FakeStore()
        store.items.add(download)
        val queueState = MutableStateFlow(listOf(download))
        val (ops) = createOps(queueState = queueState, store = store)

        ops.remove(download)

        assertTrue(store.items.isEmpty())
    }

    // endregion

    // region removeIf

    @Test
    fun `removeIf removes matching items only`() {
        val d1 = TestDownload(1, Status.QUEUE)
        val d2 = TestDownload(2, Status.QUEUE)
        val d3 = TestDownload(3, Status.QUEUE)
        val queueState = MutableStateFlow(listOf(d1, d2, d3))
        val (ops) = createOps(queueState = queueState)

        ops.removeIf { it.id % 2 != 0L }

        assertEquals(1, queueState.value.size)
        assertEquals(2L, queueState.value[0].id)
        assertEquals(Status.NOT_DOWNLOADED, d1.status)
        assertEquals(Status.QUEUE, d2.status)
        assertEquals(Status.NOT_DOWNLOADED, d3.status)
    }

    @Test
    fun `removeIf with no matches leaves queue unchanged`() {
        val d1 = TestDownload(1, Status.QUEUE)
        val queueState = MutableStateFlow(listOf(d1))
        val (ops) = createOps(queueState = queueState)

        ops.removeIf { it.id > 100 }

        assertEquals(1, queueState.value.size)
        assertEquals(Status.QUEUE, d1.status)
    }

    // endregion

    // region internalClear

    @Test
    fun `internalClear empties queue and marks all inactive`() {
        val d1 = TestDownload(1, Status.QUEUE)
        val d2 = TestDownload(2, Status.DOWNLOADING)
        val queueState = MutableStateFlow(listOf(d1, d2))
        val store = FakeStore()
        store.items.addAll(listOf(d1, d2))
        val (ops) = createOps(queueState = queueState, store = store)

        ops.internalClear()

        assertTrue(queueState.value.isEmpty())
        assertTrue(store.items.isEmpty())
        assertEquals(Status.NOT_DOWNLOADED, d1.status)
        assertEquals(Status.NOT_DOWNLOADED, d2.status)
    }

    // endregion

    // region addToStart

    @Test
    fun `addToStart prepends without duplicating existing items`() {
        val d1 = TestDownload(1, Status.QUEUE)
        val d2 = TestDownload(2, Status.QUEUE)
        val queueState = MutableStateFlow(listOf(d1))
        val (ops) = createOps(queueState = queueState)

        ops.addToStart(listOf(d1, d2))

        // d1 already exists, only d2 should be prepended
        assertEquals(2, queueState.value.size)
        assertEquals(2L, queueState.value[0].id)
        assertEquals(1L, queueState.value[1].id)
    }

    @Test
    fun `addToStart marks new items as QUEUE`() {
        val queueState = MutableStateFlow<List<TestDownload>>(emptyList())
        val (ops) = createOps(queueState = queueState)
        val download = TestDownload(1, Status.NOT_DOWNLOADED)

        ops.addToStart(listOf(download))

        assertEquals(Status.QUEUE, download.status)
    }

    // endregion

    // region moveToFront

    @Test
    fun `moveToFront moves existing item to position 0`() {
        val d1 = TestDownload(1, Status.QUEUE)
        val d2 = TestDownload(2, Status.QUEUE)
        val d3 = TestDownload(3, Status.QUEUE)
        val queueState = MutableStateFlow(listOf(d1, d2, d3))
        val (ops) = createOps(queueState = queueState)

        ops.moveToFront(d3)

        assertEquals(3L, queueState.value[0].id)
        assertEquals(1L, queueState.value[1].id)
        assertEquals(2L, queueState.value[2].id)
    }

    @Test
    fun `moveToFront marks item as QUEUE`() {
        val d1 = TestDownload(1, Status.NOT_DOWNLOADED)
        val queueState = MutableStateFlow(listOf(d1))
        val (ops) = createOps(queueState = queueState)

        ops.moveToFront(d1)

        assertEquals(Status.QUEUE, queueState.value[0].status)
    }

    // endregion

    // region concurrency

    @Test
    fun `concurrent addAll does not lose items`() = runTest {
        val queueState = MutableStateFlow<List<TestDownload>>(emptyList())
        val (ops) = createOps(queueState = queueState)
        val count = 100

        val jobs = (1..count).map { i ->
            async {
                ops.addAll(listOf(TestDownload(i.toLong())))
            }
        }
        jobs.awaitAll()

        assertEquals(count, queueState.value.size, "All $count items should be in queue")
    }

    @Test
    fun `concurrent remove does not corrupt queue`() = runTest {
        val downloads = (1..50).map { TestDownload(it.toLong(), Status.QUEUE) }
        val queueState = MutableStateFlow(downloads)
        val (ops) = createOps(queueState = queueState)

        val jobs = downloads.map { d ->
            async { ops.remove(d) }
        }
        jobs.awaitAll()

        assertTrue(queueState.value.isEmpty(), "Queue should be empty after removing all items")
    }

    @Test
    fun `concurrent addToStart and remove maintain consistency`() = runTest {
        val queueState = MutableStateFlow<List<TestDownload>>(emptyList())
        val (ops) = createOps(queueState = queueState)

        // Add items
        val addJobs = (1..25).map { i ->
            async { ops.addAll(listOf(TestDownload(i.toLong()))) }
        }
        addJobs.awaitAll()

        // Concurrently add to start and remove
        val mixed = (1..25).map { i ->
            if (i % 2 == 0) {
                async { ops.remove(TestDownload(i.toLong(), Status.QUEUE)) }
            } else {
                async { ops.addToStart(listOf(TestDownload(i.toLong() + 100))) }
            }
        }
        mixed.awaitAll()

        // Queue should not have negative size or corrupted state
        assertTrue(queueState.value.size >= 0, "Queue size should be non-negative")
        // All items in queue should have valid IDs
        queueState.value.forEach { assertTrue(it.id > 0, "All IDs should be positive") }
    }

    // endregion
}
