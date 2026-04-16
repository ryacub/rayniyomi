package eu.kanade.tachiyomi.data.download.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadQueueReducerTest {

    @Test
    fun `reorder keeps only known ids then appends remaining`() {
        val state = DownloadQueueReducerState(downloadIds = listOf(1, 2, 3), isDownloaderRunning = false)

        val result = DownloadQueueReducer.reduce(
            state,
            DownloadQueueCommand.Reorder(orderedIds = listOf(3, 99, 1)),
        )

        assertEquals(listOf(3L, 1L, 2L), result.nextState.downloadIds)
        assertEquals(
            listOf(DownloadQueueEffect.ReorderByIds(listOf(3, 1, 2))),
            result.effects,
        )
    }

    @Test
    fun `add to start ignores duplicates already in queue`() {
        val state = DownloadQueueReducerState(downloadIds = listOf(1, 2), isDownloaderRunning = false)

        val result = DownloadQueueReducer.reduce(
            state,
            DownloadQueueCommand.AddToStart(downloadIds = listOf(2, 3, 3)),
        )

        assertEquals(listOf(3L, 1L, 2L), result.nextState.downloadIds)
        assertEquals(
            listOf(
                DownloadQueueEffect.AddToStartByIds(listOf(3)),
                DownloadQueueEffect.StartDownloads,
            ),
            result.effects,
        )
    }

    @Test
    fun `remove while running emits pause remove and restart`() {
        val state = DownloadQueueReducerState(downloadIds = listOf(1, 2, 3), isDownloaderRunning = true)

        val result = DownloadQueueReducer.reduce(
            state,
            DownloadQueueCommand.RemoveByItemIds(itemIds = listOf(2)),
        )

        assertEquals(listOf(1L, 3L), result.nextState.downloadIds)
        assertEquals(
            listOf(
                DownloadQueueEffect.PauseDownloader,
                DownloadQueueEffect.RemoveByItemIds(listOf(2)),
                DownloadQueueEffect.StartDownloader,
            ),
            result.effects,
        )
    }

    @Test
    fun `remove last item while running emits stop`() {
        val state = DownloadQueueReducerState(downloadIds = listOf(2), isDownloaderRunning = true)

        val result = DownloadQueueReducer.reduce(
            state,
            DownloadQueueCommand.RemoveByItemIds(itemIds = listOf(2)),
        )

        assertEquals(emptyList<Long>(), result.nextState.downloadIds)
        assertEquals(
            listOf(
                DownloadQueueEffect.PauseDownloader,
                DownloadQueueEffect.RemoveByItemIds(listOf(2)),
                DownloadQueueEffect.StopDownloader,
            ),
            result.effects,
        )
    }

    @Test
    fun `start now emits front move and start downloads`() {
        val state = DownloadQueueReducerState(downloadIds = listOf(1, 2, 3), isDownloaderRunning = false)

        val result = DownloadQueueReducer.reduce(
            state,
            DownloadQueueCommand.StartNow(downloadId = 2),
        )

        assertEquals(listOf(2L, 1L, 3L), result.nextState.downloadIds)
        assertEquals(
            listOf(
                DownloadQueueEffect.MoveToFrontById(2),
                DownloadQueueEffect.StartDownloads,
            ),
            result.effects,
        )
    }
}
