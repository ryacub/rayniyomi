package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.IOException

class DownloadFailureClassifierTest {

    @Test
    fun `genuine cancellation classifies as cancellation and keeps the original as cause`() {
        val error = CancellationException("stopped")

        val failure = DownloadFailureClassifier.classify(error)

        assertEquals(DownloadFailureKind.CANCELLATION, failure.kind)
        assertSame(error, failure.cause)
    }

    @Test
    fun `cancellation that wraps a real cause classifies by the inner cause`() {
        val error = WrappedCancellationException(RuntimeException("HTTP error 522"))

        val failure = DownloadFailureClassifier.classify(error)

        assertEquals(DownloadFailureKind.GENERIC, failure.kind)
        assertEquals("RuntimeException", failure.code)
        assertTrue(failure.reason.orEmpty().contains("HTTP error 522"))
    }

    @Test
    fun `nested cancellation unwraps to the first real cause`() {
        val error = WrappedCancellationException(
            WrappedCancellationException(IOException("boom")),
        )

        val failure = DownloadFailureClassifier.classify(error)

        assertEquals(DownloadFailureKind.GENERIC, failure.kind)
        assertEquals("IOException", failure.code)
    }

    @Test
    fun `storage permission failure classifies as storage permission`() {
        val failure = DownloadFailureClassifier.classify(
            StoragePermissionException("EPERM (Operation not permitted)"),
        )

        assertEquals(DownloadFailureKind.STORAGE_PERMISSION, failure.kind)
        assertEquals("StoragePermissionException", failure.code)
    }

    @Test
    fun `retry exhaustion keeps the inner failure in the reason`() {
        val failure = DownloadFailureClassifier.classify(
            RetriesExhaustedException(IOException("HTTP error 504")),
        )

        assertEquals(DownloadFailureKind.RETRIES_EXHAUSTED, failure.kind)
        assertEquals("RetriesExhaustedException", failure.code)
        assertTrue(failure.reason.orEmpty().startsWith("IOException"))
        assertTrue(failure.reason.orEmpty().contains("HTTP error 504"))
    }

    @Test
    fun `low storage failure classifies as low storage with a fixed code`() {
        val failure = DownloadFailureClassifier.classify(
            LowStorageException("No space left on device"),
        )

        assertEquals(DownloadFailureKind.LOW_STORAGE, failure.kind)
        assertEquals("LOW_STORAGE", failure.code)
    }

    @Test
    fun `an ordinary io failure classifies as generic`() {
        val failure = DownloadFailureClassifier.classify(IOException("HTTP error 500"))

        assertEquals(DownloadFailureKind.GENERIC, failure.kind)
        assertEquals("IOException", failure.code)
        assertEquals("HTTP error 500", failure.reason)
    }

    @Test
    fun `a failure with no message keeps a null reason`() {
        val failure = DownloadFailureClassifier.classify(IOException())

        assertEquals("IOException", failure.code)
        assertNull(failure.reason)
    }

    @Test
    fun `permission check accepts a filesystem failure that reports EPERM`() {
        val e = FileNotFoundException(
            "/data/data/files/downloads/Test/Ch1_tmp/001.tmp: open failed: EPERM (Operation not permitted)",
        )

        assertTrue(DownloadFailureClassifier.isPermissionFailure(e))
    }

    @Test
    fun `permission check rejects an http body that says permission denied`() {
        val e = RuntimeException("HTTP 403: Permission denied")

        assertFalse(DownloadFailureClassifier.isPermissionFailure(e))
    }

    @Test
    fun `low storage check rejects a blank message`() {
        assertFalse(DownloadFailureClassifier.isLowStorageFailure(null))
        assertFalse(DownloadFailureClassifier.isLowStorageFailure("  "))
    }

    @Test
    fun `low storage check accepts the known out of space messages`() {
        assertTrue(DownloadFailureClassifier.isLowStorageFailure("No space left on device"))
        assertTrue(DownloadFailureClassifier.isLowStorageFailure("ENOSPC"))
        assertTrue(DownloadFailureClassifier.isLowStorageFailure("disk full"))
        assertTrue(DownloadFailureClassifier.isLowStorageFailure("insufficient storage"))
    }
}

/**
 * A cancellation exception that carries a real failure as its cause.
 */
private class WrappedCancellationException(cause: Throwable) : CancellationException("wrapped") {
    init {
        initCause(cause)
    }
}
