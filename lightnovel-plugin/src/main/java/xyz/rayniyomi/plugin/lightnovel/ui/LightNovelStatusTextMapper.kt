package xyz.rayniyomi.plugin.lightnovel.ui

import android.content.Context
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferDisplayStatus
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferSnapshot
import xyz.rayniyomi.plugin.lightnovel.R

fun LightNovelTransferSnapshot.displayReasonText(context: Context): String {
    return when (displayStatus) {
        LightNovelTransferDisplayStatus.PREPARING -> context.getString(R.string.import_preparing)
        LightNovelTransferDisplayStatus.IMPORTING -> {
            val percent = progressPercent
            if (percent == null) {
                context.getString(R.string.import_progress_unknown)
            } else {
                context.getString(R.string.import_progress_percent, percent)
            }
        }
        LightNovelTransferDisplayStatus.VERIFYING -> context.getString(R.string.import_verifying)
        LightNovelTransferDisplayStatus.STALLED -> context.getString(R.string.import_stalled)
        LightNovelTransferDisplayStatus.COMPLETED -> context.getString(R.string.import_completed)
        LightNovelTransferDisplayStatus.FAILED -> {
            val reason = lastErrorReason?.takeIf { it.isNotBlank() } ?: context.getString(R.string.import_failed)
            context.getString(R.string.import_failed_reason, reason)
        }
        LightNovelTransferDisplayStatus.PAUSED_LOW_STORAGE -> context.getString(R.string.import_low_storage)
    }
}
