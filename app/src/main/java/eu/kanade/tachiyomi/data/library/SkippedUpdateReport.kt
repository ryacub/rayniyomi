package eu.kanade.tachiyomi.data.library

import android.content.Context
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.notification.ErrorLogWriteOutcome
import eu.kanade.tachiyomi.data.notification.writeErrorLogOutcome
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

internal data class SkippedUpdate(
    val reason: AutoUpdateSkipReason,
    val source: String,
    val title: String,
)

internal val AutoUpdateSkipReason.labelRes: StringResource
    get() = when (this) {
        AutoUpdateSkipReason.NOT_ALWAYS_UPDATE -> MR.strings.skipped_reason_not_always_update
        AutoUpdateSkipReason.COMPLETED -> MR.strings.skipped_reason_completed
        AutoUpdateSkipReason.NOT_CAUGHT_UP -> MR.strings.skipped_reason_not_caught_up
        AutoUpdateSkipReason.NOT_STARTED -> MR.strings.skipped_reason_not_started
        AutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD -> MR.strings.skipped_reason_not_in_release_period
    }

/** Scheduled runs stay silent about normal skips; only a manual run reports them (R1074). */
internal fun skippedUpdatesForReport(isManualRun: Boolean, skipped: List<SkippedUpdate>): List<SkippedUpdate> =
    if (isManualRun) skipped else emptyList()

/** Uses the same `! reason / # source / - title` layout as the update error log. */
internal fun formatSkippedUpdateReport(
    header: String,
    skipped: List<SkippedUpdate>,
    reasonLabel: (AutoUpdateSkipReason) -> String,
): String = buildString {
    append(header).append("\n\n")
    skipped.groupBy { it.reason }.toSortedMap().forEach { (reason, byReason) ->
        append("\n! ").append(reasonLabel(reason)).append('\n')
        byReason.groupBy { it.source }.toSortedMap().forEach { (source, bySource) ->
            append("  # ").append(source).append('\n')
            bySource.map { it.title }.sorted().forEach { append("    - ").append(it).append('\n') }
        }
    }
}

internal fun Context.writeSkippedUpdateReport(fileName: String, skipped: List<SkippedUpdate>): ErrorLogWriteOutcome {
    return writeErrorLogOutcome(hasErrors = skipped.isNotEmpty()) {
        val report = formatSkippedUpdateReport(
            header = stringResource(MR.strings.library_skipped_help),
            skipped = skipped,
            reasonLabel = { stringResource(it.labelRes) },
        )
        createFileInCacheDir(fileName).apply { writeText(report) }
    }
}
