package eu.kanade.tachiyomi.data.download.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DownloadStatusTrackerTest {

    @Test
    fun `should mark stalled after threshold without retry`() {
        val download = TestStatusSnapshot(
            isRunningTransfer = true,
            displayStatus = DownloadDisplayStatus.DOWNLOADING,
            lastProgressAt = 1_000L,
            retryAttempt = 0,
        )

        DownloadStatusTracker.shouldMarkStalled(download, 11_001L) shouldBe true
    }

    @Test
    fun `should not mark stalled during retry`() {
        val download = TestStatusSnapshot(
            isRunningTransfer = true,
            displayStatus = DownloadDisplayStatus.RETRYING,
            lastProgressAt = 1_000L,
            retryAttempt = 1,
        )

        DownloadStatusTracker.shouldMarkStalled(download, 20_000L) shouldBe false
    }

    @Test
    fun `summarize should count downloading waiting and stalled statuses`() {
        val downloads = listOf(
            TestStatusSnapshot(displayStatus = DownloadDisplayStatus.DOWNLOADING),
            TestStatusSnapshot(displayStatus = DownloadDisplayStatus.DOWNLOADING),
            TestStatusSnapshot(displayStatus = DownloadDisplayStatus.WAITING_FOR_SLOT),
            TestStatusSnapshot(displayStatus = DownloadDisplayStatus.STALLED),
        )

        val summary = DownloadStatusTracker.summarize(downloads)

        summary.downloading shouldBe 2
        summary.waitingForSlot shouldBe 1
        summary.stalled shouldBe 1
    }

    private data class TestStatusSnapshot(
        override val isRunningTransfer: Boolean = false,
        override val displayStatus: DownloadDisplayStatus = DownloadDisplayStatus.PREPARING,
        override val lastProgressAt: Long = 0L,
        override val retryAttempt: Int = 0,
        override val lastErrorReason: String? = null,
    ) : DownloadStatusSnapshot
}
