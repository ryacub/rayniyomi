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

    @Test
    fun `coalesces determinate progress updates when percent is unchanged`() {
        var now = 1_000L
        val tracker = NovelImportStatusTracker(nowMs = { now })

        tracker.onImportProgress(bytesRead = 10, contentLength = 1_000)
        val firstProgressAt = tracker.status.value.lastProgressAt

        now = 1_050L
        tracker.onImportProgress(bytesRead = 11, contentLength = 1_000)

        tracker.status.value.progressPercent shouldBe 1
        tracker.status.value.lastProgressAt shouldBe firstProgressAt
    }

    @Test
    fun `throttles unknown-size progress emissions`() {
        var now = 1_000L
        val tracker = NovelImportStatusTracker(nowMs = { now })

        tracker.onImportProgress(bytesRead = 10, contentLength = null)
        val firstProgressAt = tracker.status.value.lastProgressAt

        now = 1_100L
        tracker.onImportProgress(bytesRead = 40, contentLength = null)
        tracker.status.value.lastProgressAt shouldBe firstProgressAt

        now = 1_400L
        tracker.onImportProgress(bytesRead = 100, contentLength = null)
        tracker.status.value.lastProgressAt shouldBe 1_400L
    }
}
