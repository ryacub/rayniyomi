package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * A cancellation exception that carries a real failure as its cause.
 */
private class WrappedCancellation(cause: Throwable) : CancellationException("wrapped") {
    init {
        initCause(cause)
    }
}

class DownloadFailurePolicyTest {

    @Test
    fun `forDownloadJob rethrows a genuine cancellation on a dead scope`() {
        val action = DownloadFailurePolicy.forDownloadJob(
            CancellationException("stopped"),
            scopeActive = false,
        )

        assertEquals(DownloadFailureAction.Rethrow, action)
    }

    @Test
    fun `forDownloadJob reports a cancellation shaped failure while the scope is active`() {
        val action = DownloadFailurePolicy.forDownloadJob(
            CancellationException("cancelled inside source"),
            scopeActive = true,
        ) as DownloadFailureAction.Report

        assertEquals(DownloadFailureKind.CANCELLATION, action.failure.kind)
        assertEquals(action.failure.code, action.reasonFallback)
    }

    @Test
    fun `forDownloadJob reports a wrapped cancellation cause even on a dead scope`() {
        val action = DownloadFailurePolicy.forDownloadJob(
            WrappedCancellation(RuntimeException("HTTP error 522")),
            scopeActive = false,
        ) as DownloadFailureAction.Report

        assertEquals(DownloadFailureKind.GENERIC, action.failure.kind)
        assertEquals("RuntimeException", action.failure.code)
    }

    @Test
    fun `forDownloadJob reports a plain failure with the code as the reason fallback`() {
        val action = DownloadFailurePolicy.forDownloadJob(
            IOException("boom"),
            scopeActive = true,
        ) as DownloadFailureAction.Report

        assertEquals("IOException", action.reasonFallback)
    }

    @Test
    fun `forItem rethrows any cancellation`() {
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forItem(CancellationException("plain")),
        )
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forItem(WrappedCancellation(RuntimeException("real"))),
        )
    }

    @Test
    fun `forItem silences a low storage failure`() {
        val action = DownloadFailurePolicy.forItem(LowStorageException("ENOSPC"))
            as DownloadFailureAction.Silence

        assertEquals(DownloadFailureKind.LOW_STORAGE, action.failure.kind)
    }

    @Test
    fun `forItem reports a plain failure`() {
        val action = DownloadFailurePolicy.forItem(IOException("boom")) as DownloadFailureAction.Report

        assertEquals(DownloadFailureKind.GENERIC, action.failure.kind)
        assertNull(action.reasonFallback)
    }

    @Test
    fun `forVideoFetch pauses on low storage`() {
        val cause = LowStorageException("No space left on device")
        val action = DownloadFailurePolicy.forVideoFetch(cause) as DownloadFailureAction.PauseLowStorage

        assertEquals(DownloadFailureKind.LOW_STORAGE, action.failure.kind)
        assertEquals("No space left on device", action.failure.message)
        assertTrue(action.failure.cause === cause)
    }

    @Test
    fun `forVideoFetch rethrows a storage permission failure`() {
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forVideoFetch(StoragePermissionException("EPERM")),
        )
    }

    @Test
    fun `forVideoFetch rethrows an exhausted retry failure`() {
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forVideoFetch(RetriesExhaustedException(IOException("HTTP error 504"))),
        )
    }

    @Test
    fun `forVideoFetch rethrows cancellation`() {
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forVideoFetch(CancellationException("cancelled")),
        )
    }

    @Test
    fun `forVideoFetch reports a plain failure`() {
        val action = DownloadFailurePolicy.forVideoFetch(IOException("boom")) as DownloadFailureAction.Report

        assertEquals(DownloadFailureKind.GENERIC, action.failure.kind)
    }

    @Test
    fun `forImageFetch rethrows low storage instead of pausing`() {
        // The image fetch is the one intended anime/manga divergence: manga low
        // storage goes up to the item policy, which returns without reporting twice.
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forImageFetch(LowStorageException("No space left on device")),
        )
    }

    @Test
    fun `forImageFetch rethrows permission and exhausted retries`() {
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forImageFetch(StoragePermissionException("EPERM")),
        )
        assertEquals(
            DownloadFailureAction.Rethrow,
            DownloadFailurePolicy.forImageFetch(RetriesExhaustedException(IOException("boom"))),
        )
    }

    @Test
    fun `forImageFetch reports a plain failure`() {
        val action = DownloadFailurePolicy.forImageFetch(IOException("boom")) as DownloadFailureAction.Report

        assertEquals(DownloadFailureKind.GENERIC, action.failure.kind)
    }

    @Test
    fun `lowStorageFailure carries the localized message and no cause`() {
        val failure = DownloadFailurePolicy.lowStorageFailure("mocked")

        assertEquals(DownloadFailureKind.LOW_STORAGE, failure.kind)
        assertEquals("LOW_STORAGE", failure.code)
        assertEquals("mocked", failure.reason)
        assertNull(failure.cause)
    }

    @Test
    fun `incompleteOutputFailure sends no notification`() {
        val failure = DownloadFailurePolicy.incompleteOutputFailure("Incomplete download output")

        assertEquals(DownloadFailureKind.INCOMPLETE_OUTPUT, failure.kind)
        assertEquals("INCOMPLETE", failure.code)
        assertEquals(DownloadFailureNotification.NONE, failure.kind.notification)
    }

    @Test
    fun `lowStorageFailure carries an optional cause`() {
        val cause = IOException("No space left on device")
        val failure = DownloadFailurePolicy.lowStorageFailure(cause.message, cause)

        assertEquals(DownloadFailureKind.LOW_STORAGE, failure.kind)
        assertEquals("No space left on device", failure.reason)
        assertTrue(failure.cause === cause)
        assertEquals(DownloadFailureNotification.WARNING, failure.kind.notification)
    }
}
