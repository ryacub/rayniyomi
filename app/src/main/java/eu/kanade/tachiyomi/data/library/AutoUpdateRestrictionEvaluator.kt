package eu.kanade.tachiyomi.data.library

import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD

internal data class AutoUpdateCandidate(
    val alwaysUpdate: Boolean,
    val isCompleted: Boolean,
    val hasUnviewed: Boolean,
    val hasStarted: Boolean,
    val totalCount: Long,
    val nextUpdate: Long,
)

internal enum class AutoUpdateSkipReason {
    NOT_ALWAYS_UPDATE,
    COMPLETED,
    NOT_CAUGHT_UP,
    NOT_STARTED,
    OUTSIDE_RELEASE_PERIOD,
}

internal fun evaluateAutoUpdateCandidate(
    candidate: AutoUpdateCandidate,
    restrictions: Set<String>,
    fetchWindowUpperBound: Long,
): AutoUpdateSkipReason? {
    return when {
        !candidate.alwaysUpdate -> AutoUpdateSkipReason.NOT_ALWAYS_UPDATE
        ENTRY_NON_COMPLETED in restrictions && candidate.isCompleted -> AutoUpdateSkipReason.COMPLETED
        ENTRY_HAS_UNVIEWED in restrictions && candidate.hasUnviewed -> AutoUpdateSkipReason.NOT_CAUGHT_UP
        ENTRY_NON_VIEWED in restrictions && candidate.totalCount > 0L && !candidate.hasStarted ->
            AutoUpdateSkipReason.NOT_STARTED
        ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && candidate.nextUpdate > fetchWindowUpperBound ->
            AutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD
        else -> null
    }
}
