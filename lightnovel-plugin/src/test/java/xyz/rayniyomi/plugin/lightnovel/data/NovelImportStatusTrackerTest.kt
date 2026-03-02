package xyz.rayniyomi.plugin.lightnovel.data

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferDisplayStatus

class NovelImportStatusTrackerTest {

    @Test
    fun `marks stalled after threshold and resumes on next progress`() {
        var now = 1_000L
        val tracker = NovelImportStatusTracker(nowMs = { now })

        tracker.onImportProgress(bytesRead = 10, contentLength = 100)
        now = 11_100L
        tracker.updateStalledIfNeeded()
        tracker.status.value.displayStatus shouldBe LightNovelTransferDisplayStatus.STALLED

        now = 11_200L
        tracker.onImportProgress(bytesRead = 20, contentLength = 100)
        tracker.status.value.displayStatus shouldBe LightNovelTransferDisplayStatus.IMPORTING
    }

    @Test
    fun `keeps unknown-size progress indeterminate`() {
        val tracker = NovelImportStatusTracker(nowMs = { 10_000L })

        tracker.onImportProgress(bytesRead = 256, contentLength = null)

        tracker.status.value.displayStatus shouldBe LightNovelTransferDisplayStatus.IMPORTING
        tracker.status.value.progressPercent shouldBe null
    }
}
