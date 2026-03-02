package xyz.rayniyomi.plugin.lightnovel.ui

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferDisplayStatus
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.data.NovelImportStatus

class LightNovelStatusTextMapperTest {
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `maps importing unknown size to unknown text`() {
        every { context.getString(R.string.import_progress_unknown) } returns "unknown"

        NovelImportStatus(
            displayStatus = LightNovelTransferDisplayStatus.IMPORTING,
            progressPercent = null,
        ).displayReasonText(context) shouldBe "unknown"
    }

    @Test
    fun `maps failed reason with fallback`() {
        every { context.getString(R.string.import_failed) } returns "failed"
        every { context.getString(R.string.import_failed_reason, "failed") } returns "Import failed: failed"

        NovelImportStatus(
            displayStatus = LightNovelTransferDisplayStatus.FAILED,
            lastErrorReason = null,
        ).displayReasonText(context) shouldBe "Import failed: failed"
    }
}
