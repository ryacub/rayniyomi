package eu.kanade.tachiyomi.ui.download

import android.content.Context
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.TestStatusSnapshot
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class DownloadStatusTextMapperTest {

    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
    }

    @Test
    fun `maps each status to expected text behavior`() {
        every { context.stringResource(MR.strings.download_status_waiting_slot) } returns "slot"
        every { context.stringResource(MR.strings.download_notifier_no_network) } returns "network"
        every { context.stringResource(MR.strings.download_notifier_text_only_wifi) } returns "wifi"
        every { context.stringResource(MR.strings.download_status_preparing) } returns "preparing"
        every { context.stringResource(MR.strings.download_status_connecting) } returns "connecting"
        every { context.stringResource(MR.strings.download_status_stalled) } returns "stalled"
        every { context.stringResource(MR.strings.download_status_retrying_attempt, 3) } returns "retry-3"
        every { context.stringResource(MR.strings.download_notifier_download_paused) } returns "paused"
        every { context.stringResource(MR.strings.download_status_low_storage) } returns "low-storage"
        every { context.stringResource(MR.strings.download_status_verifying) } returns "verifying"
        every { context.stringResource(MR.strings.download_status_completed) } returns "completed"
        every { context.stringResource(MR.strings.download_notifier_unknown_error) } returns "unknown"

        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.WAITING_FOR_SLOT).displayReasonText(context) shouldBe
            "slot"
        TestStatusSnapshot(
            displayStatus = DownloadDisplayStatus.WAITING_FOR_NETWORK,
        ).displayReasonText(context) shouldBe
            "network"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.WAITING_FOR_WIFI).displayReasonText(context) shouldBe
            "wifi"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.PREPARING).displayReasonText(context) shouldBe
            "preparing"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.CONNECTING).displayReasonText(context) shouldBe
            "connecting"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.DOWNLOADING).displayReasonText(context) shouldBe null
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.STALLED).displayReasonText(context) shouldBe "stalled"
        TestStatusSnapshot(
            displayStatus = DownloadDisplayStatus.RETRYING,
            retryAttempt = 3,
        ).displayReasonText(context) shouldBe
            "retry-3"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.PAUSED_BY_USER).displayReasonText(context) shouldBe
            "paused"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.PAUSED_LOW_STORAGE).displayReasonText(context) shouldBe
            "low-storage"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.VERIFYING).displayReasonText(context) shouldBe
            "verifying"
        TestStatusSnapshot(displayStatus = DownloadDisplayStatus.COMPLETED).displayReasonText(context) shouldBe
            "completed"
    }

    @Test
    fun `failed status prefers explicit error reason`() {
        TestStatusSnapshot(
            displayStatus = DownloadDisplayStatus.FAILED,
            lastErrorReason = "custom reason",
        ).displayReasonText(context) shouldBe "custom reason"
    }

    @Test
    fun `failed status falls back to unknown error string`() {
        every { context.stringResource(MR.strings.download_notifier_unknown_error) } returns "unknown"

        TestStatusSnapshot(
            displayStatus = DownloadDisplayStatus.FAILED,
            lastErrorReason = null,
        ).displayReasonText(context) shouldBe "unknown"
    }
}
