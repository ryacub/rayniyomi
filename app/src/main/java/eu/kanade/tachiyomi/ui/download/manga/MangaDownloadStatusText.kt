package eu.kanade.tachiyomi.ui.download.manga

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

fun MangaDownload.displayReasonText(context: Context): String? {
    return when (displayStatus) {
        MangaDownload.DisplayStatus.WAITING_FOR_SLOT -> context.stringResource(MR.strings.download_status_waiting_slot)
        MangaDownload.DisplayStatus.WAITING_FOR_NETWORK -> context.stringResource(
            MR.strings.download_notifier_no_network,
        )
        MangaDownload.DisplayStatus.WAITING_FOR_WIFI -> context.stringResource(
            MR.strings.download_notifier_text_only_wifi,
        )
        MangaDownload.DisplayStatus.PREPARING -> context.stringResource(MR.strings.download_status_preparing)
        MangaDownload.DisplayStatus.CONNECTING -> context.stringResource(MR.strings.download_status_connecting)
        MangaDownload.DisplayStatus.DOWNLOADING -> null
        MangaDownload.DisplayStatus.STALLED -> context.stringResource(MR.strings.download_status_stalled)
        MangaDownload.DisplayStatus.RETRYING -> context.stringResource(
            MR.strings.download_status_retrying_attempt,
            retryAttempt,
        )
        MangaDownload.DisplayStatus.PAUSED_BY_USER -> context.stringResource(
            MR.strings.download_notifier_download_paused,
        )
        MangaDownload.DisplayStatus.PAUSED_LOW_STORAGE -> context.stringResource(MR.strings.download_status_low_storage)
        MangaDownload.DisplayStatus.VERIFYING -> context.stringResource(MR.strings.download_status_verifying)
        MangaDownload.DisplayStatus.COMPLETED -> context.stringResource(MR.strings.download_status_completed)
        MangaDownload.DisplayStatus.FAILED -> {
            lastErrorReason ?: context.stringResource(MR.strings.download_notifier_unknown_error)
        }
    }
}
