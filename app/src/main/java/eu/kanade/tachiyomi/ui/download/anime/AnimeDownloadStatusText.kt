package eu.kanade.tachiyomi.ui.download.anime

import android.content.Context
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

fun AnimeDownload.displayReasonText(context: Context): String? {
    return when (displayStatus) {
        AnimeDownload.DisplayStatus.WAITING_FOR_SLOT -> context.stringResource(MR.strings.download_status_waiting_slot)
        AnimeDownload.DisplayStatus.WAITING_FOR_NETWORK -> context.stringResource(
            MR.strings.download_notifier_no_network,
        )
        AnimeDownload.DisplayStatus.WAITING_FOR_WIFI -> context.stringResource(
            MR.strings.download_notifier_text_only_wifi,
        )
        AnimeDownload.DisplayStatus.PREPARING -> context.stringResource(MR.strings.download_status_preparing)
        AnimeDownload.DisplayStatus.CONNECTING -> context.stringResource(MR.strings.download_status_connecting)
        AnimeDownload.DisplayStatus.DOWNLOADING -> null
        AnimeDownload.DisplayStatus.STALLED -> context.stringResource(MR.strings.download_status_stalled)
        AnimeDownload.DisplayStatus.RETRYING -> context.stringResource(
            MR.strings.download_status_retrying_attempt,
            retryAttempt,
        )
        AnimeDownload.DisplayStatus.PAUSED_BY_USER -> context.stringResource(
            MR.strings.download_notifier_download_paused,
        )
        AnimeDownload.DisplayStatus.PAUSED_LOW_STORAGE -> context.stringResource(MR.strings.download_status_low_storage)
        AnimeDownload.DisplayStatus.VERIFYING -> context.stringResource(MR.strings.download_status_verifying)
        AnimeDownload.DisplayStatus.COMPLETED -> context.stringResource(MR.strings.download_status_completed)
        AnimeDownload.DisplayStatus.FAILED -> {
            lastErrorReason ?: context.stringResource(MR.strings.download_notifier_unknown_error)
        }
    }
}
