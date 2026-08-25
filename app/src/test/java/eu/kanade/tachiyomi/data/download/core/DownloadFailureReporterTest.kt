package eu.kanade.tachiyomi.data.download.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * A download that records its last failure, without any manga type.
 */
private class FakeDownload : DownloadFailureTarget {
    override var lastErrorCode: String? = null
    override var lastErrorReason: String? = null
}

class DownloadFailureReporterTest {

    private var errorNotifications = 0
    private var warningNotifications = 0
    private var lastNotifiedMessage: String? = null

    private fun newReporter() = DownloadFailureReporter<FakeDownload>(
        notifyError = { _, failure ->
            errorNotifications++
            lastNotifiedMessage = failure.message
        },
        notifyWarning = { _, _ ->
            warningNotifications++
        },
    )

    @Test
    fun `report writes the code and the reason`() {
        val reporter = newReporter()
        val download = FakeDownload()

        reporter.report(
            download,
            DownloadFailure(DownloadFailureKind.GENERIC, "boom", RuntimeException("boom")),
        )

        assertEquals("RuntimeException", download.lastErrorCode)
        assertEquals("boom", download.lastErrorReason)
    }

    @Test
    fun `report sends an error notification for a generic failure`() {
        val reporter = newReporter()
        val download = FakeDownload()

        reporter.report(
            download,
            DownloadFailure(DownloadFailureKind.GENERIC, "boom", RuntimeException("boom")),
        )

        assertEquals(1, errorNotifications)
        assertEquals(0, warningNotifications)
    }

    @Test
    fun `report sends a warning for low storage and never an error`() {
        val reporter = newReporter()
        val download = FakeDownload()

        reporter.report(
            download,
            DownloadFailure(DownloadFailureKind.LOW_STORAGE, "No space left on device", null),
        )

        assertEquals(1, warningNotifications)
        assertEquals(0, errorNotifications)
        assertEquals("LOW_STORAGE", download.lastErrorCode)
    }

    @Test
    fun `report sends no notification for incomplete output`() {
        val reporter = newReporter()
        val download = FakeDownload()

        reporter.report(
            download,
            DownloadFailure(DownloadFailureKind.INCOMPLETE_OUTPUT, "Incomplete chapter output", null),
        )

        assertEquals(0, errorNotifications)
        assertEquals(0, warningNotifications)
        assertEquals("INCOMPLETE", download.lastErrorCode)
        assertEquals("Incomplete chapter output", download.lastErrorReason)
    }

    @Test
    fun `report passes the raw message to the notifier not the fallback reason`() {
        val reporter = newReporter()
        val download = FakeDownload()

        reporter.report(download, DownloadFailure(DownloadFailureKind.GENERIC, null, IOException()))

        assertEquals("IOException", download.lastErrorCode)
        assertNull(download.lastErrorReason)
        assertNull(lastNotifiedMessage)
    }

    @Test
    fun `report uses the reason fallback when the caller passes one`() {
        val reporter = newReporter()
        val download = FakeDownload()

        reporter.report(
            download,
            DownloadFailure(DownloadFailureKind.GENERIC, null, IOException()),
            reasonFallback = "IOException",
        )

        assertEquals("IOException", download.lastErrorCode)
        assertEquals("IOException", download.lastErrorReason)
        assertNull(lastNotifiedMessage)
    }

    @Test
    fun `clear resets both fields and sends no notification`() {
        val reporter = newReporter()
        val download = FakeDownload().apply {
            lastErrorCode = "INCOMPLETE"
            lastErrorReason = "Incomplete chapter output"
        }

        reporter.clear(download)

        assertNull(download.lastErrorCode)
        assertNull(download.lastErrorReason)
        assertEquals(0, errorNotifications)
        assertEquals(0, warningNotifications)
    }

    @Test
    fun `report sends an error notification for storage permission and retry exhaustion`() {
        val reporter = newReporter()

        reporter.report(
            FakeDownload(),
            DownloadFailure(DownloadFailureKind.STORAGE_PERMISSION, "EPERM", IOException("EPERM")),
        )
        assertEquals(1, errorNotifications)
        assertEquals(0, warningNotifications)

        reporter.report(
            FakeDownload(),
            DownloadFailure(
                DownloadFailureKind.RETRIES_EXHAUSTED,
                "IOException: HTTP error 504",
                IOException("HTTP error 504"),
            ),
        )
        assertEquals(2, errorNotifications)
        assertEquals(0, warningNotifications)
    }
}
