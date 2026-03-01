package eu.kanade.tachiyomi.ui.download

import android.content.Context
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadStatusSnapshot
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

fun DownloadStatusSnapshot.displayReasonText(context: Context): String? {
    return when (displayStatus) {
        DownloadDisplayStatus.WAITING_FOR_SLOT -> context.stringResource(MR.strings.download_status_waiting_slot)
        DownloadDisplayStatus.WAITING_FOR_NETWORK -> context.stringResource(
            MR.strings.download_notifier_no_network,
        )
        DownloadDisplayStatus.WAITING_FOR_WIFI -> context.stringResource(
            MR.strings.download_notifier_text_only_wifi,
        )
        DownloadDisplayStatus.PREPARING -> context.stringResource(MR.strings.download_status_preparing)
        DownloadDisplayStatus.CONNECTING -> context.stringResource(MR.strings.download_status_connecting)
        DownloadDisplayStatus.DOWNLOADING -> null
        DownloadDisplayStatus.STALLED -> context.stringResource(MR.strings.download_status_stalled)
        DownloadDisplayStatus.RETRYING -> context.stringResource(
            MR.strings.download_status_retrying_attempt,
            retryAttempt,
        )
        DownloadDisplayStatus.PAUSED_BY_USER -> context.stringResource(
            MR.strings.download_notifier_download_paused,
        )
        DownloadDisplayStatus.PAUSED_LOW_STORAGE -> context.stringResource(MR.strings.download_status_low_storage)
        DownloadDisplayStatus.VERIFYING -> context.stringResource(MR.strings.download_status_verifying)
        DownloadDisplayStatus.COMPLETED -> context.stringResource(MR.strings.download_status_completed)
        DownloadDisplayStatus.FAILED -> {
            lastErrorReason ?: context.stringResource(MR.strings.download_notifier_unknown_error)
        }
    }
}
