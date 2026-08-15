package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `cancels the monitors in reverse list order`() = runTest {
        val order = mutableListOf<String>()
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                monitors = listOf(
                    {
                        try {
                            firstStarted.complete(Unit)
                            awaitForever()
                        } finally {
                            order += "first"
                        }
                    },
                    {
                        try {
                            secondStarted.complete(Unit)
                            awaitForever()
                        } finally {
                            order += "second"
                        }
                    },
                ),
                block = {
                    firstStarted.await()
                    secondStarted.await()
                },
            )
        }

        assertEquals(listOf("second", "first"), order)
    }

    @Test
    fun `propagates the exception when a monitor throws`() = runTest {
        var thrown: Throwable? = null

        withTimeoutOrNull(TIMEOUT_MS) {
            try {
                DownloadMonitors.withMonitors(
                    monitors = listOf({
                        throw IllegalStateException("monitor boom")
                    }),
                    block = { awaitForever() },
                )
            } catch (e: IllegalStateException) {
                thrown = e
            }
        }

        assertEquals("monitor boom", thrown?.message)
    }

    @Test
    fun `cancels every monitor when the caller is cancelled`() = runTest {
        var cancelled = false
        val started = CompletableDeferred<Unit>()

        val job = launch {
            DownloadMonitors.withMonitors(
                monitors = listOf({
                    try {
                        started.complete(Unit)
                        awaitForever()
                    } finally {
                        cancelled = true
                    }
                }),
                block = { awaitForever() },
            )
        }

        started.await()
        job.cancel()
        job.join()

        assertTrue(cancelled, "the monitor must be cancelled when the caller is cancelled")
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
