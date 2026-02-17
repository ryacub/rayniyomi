package tachiyomi.domain.library.model

import tachiyomi.core.common.preference.TriState

/**
 * Filter parameters for DB-layer library queries.
 *
 * filterUnread: filter by unread/unseen count > 0
 * filterStarted: filter by read/seen count > 0
 * filterBookmarked: filter by bookmark count > 0
 * filterCompleted: filter by completed status
 * filterIntervalCustom: filter by custom fetch interval (fetchInterval < 0)
 */
data class LibraryFilter(
    val filterUnread: TriState = TriState.DISABLED,
    val filterStarted: TriState = TriState.DISABLED,
    val filterBookmarked: TriState = TriState.DISABLED,
    val filterCompleted: TriState = TriState.DISABLED,
    val filterIntervalCustom: TriState = TriState.DISABLED,
) {
    val hasActiveFilters: Boolean
        get() = filterUnread != TriState.DISABLED ||
            filterStarted != TriState.DISABLED ||
            filterBookmarked != TriState.DISABLED ||
            filterCompleted != TriState.DISABLED ||
            filterIntervalCustom != TriState.DISABLED

    companion object {
        val NONE = LibraryFilter()
    }
}
