package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadMonitorsTest {

    @Test
    fun `returns the block result when a monitor never ends`() = runTest {
        val progress = MutableStateFlow(0)

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                monitors = listOf({ progress.collect { } }),
                block = { "done" },
            )
        }

        assertNotNull(result, "withMonitors must return while an endless monitor runs")
        assertEquals("done", result)
    }

    @Test
    fun `returns when every monitor never ends`() = runTest {
        val progress = MutableStateFlow(0)

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                monitors = listOf(
                    { progress.collect { } },
                    {
                        while (true) {
                            delay(1_000)
                        }
                    },
                ),
                block = { "done" },
            )
        }

        assertEquals("done", result)
    }

    @Test
    fun `runs every monitor while the block runs`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                monitors = listOf(
                    {
                        firstStarted.complete(Unit)
                        awaitForever()
                    },
                    {
                        secondStarted.complete(Unit)
                        awaitForever()
                    },
                ),
                block = {
                    firstStarted.await()
                    secondStarted.await()
                    "done"
                },
            )
        }

        assertEquals("done", result)
    }

    @Test
    fun `cancels every monitor after the block returns`() = runTest {
        var firstCancelled = false
        var secondCancelled = false

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                monitors = listOf(
                    {
                        try {
                            awaitForever()
                        } finally {
                            firstCancelled = true
                        }
                    },
                    {
                        try {
                            awaitForever()
                        } finally {
                            secondCancelled = true
                        }
                    },
                ),
                block = { delay(10) },
            )
        }

        assertTrue(firstCancelled, "the first monitor must be cancelled")
        assertTrue(secondCancelled, "the second monitor must be cancelled")
    }

    @Test
    fun `cancels every monitor when the block throws`() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        var thrown: Throwable? = null

        withTimeoutOrNull(TIMEOUT_MS) {
            try {
                DownloadMonitors.withMonitors(
                    monitors = listOf(
                        {
                            try {
                                started.complete(Unit)
                                awaitForever()
                            } finally {
                                cancelled = true
                            }
                        },
                    ),
                    block = {
                        started.await()
                        throw IllegalStateException("boom")
                    },
                )
            } catch (e: IllegalStateException) {
                thrown = e
            }
        }

        assertTrue(cancelled, "the monitor must be cancelled when the block throws")
        assertEquals("boom", thrown?.message)
    }

    @Test
    fun `runs the block when the monitor list is empty`() = runTest {
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(monitors = emptyList(), block = { "done" })
        }

        assertEquals("done", result)
    }

    @Test
    fun `stops a monitor that ignores cancellation of its children`() = runTest {
        var blockReturned = false

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                monitors = listOf({ MutableStateFlow(0).collect { } }),
                block = {
                    blockReturned = true
                    "done"
                },
            )
        }

        assertTrue(blockReturned)
        assertFalse(result == null, "withMonitors must not hang after the block returns")
    }

    private suspend fun awaitForever() {
        while (true) {
            delay(1_000)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
