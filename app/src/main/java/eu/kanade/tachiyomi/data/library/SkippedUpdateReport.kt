package eu.kanade.tachiyomi.data.library

import dev.icerock.moko.resources.StringResource
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
