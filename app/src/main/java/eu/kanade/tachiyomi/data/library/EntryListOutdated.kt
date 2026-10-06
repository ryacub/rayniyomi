package eu.kanade.tachiyomi.data.library

import tachiyomi.domain.category.model.Category

data class FetchWindow(val start: Long, val end: Long) {
    companion object {
        fun from(window: Pair<Long, Long>) = FetchWindow(start = window.first, end = window.second)
    }
}

data class EntryUpdateContext(
    val restrictions: Set<String>,
    val isInUpdateCategories: Boolean,
    val fetchWindow: FetchWindow,
)

internal fun isInAutoUpdateCategories(
    entryCategoryIds: List<Long>,
    included: Set<Long>,
    excluded: Set<Long>,
): Boolean {
    val categoryIds = entryCategoryIds.ifEmpty { listOf(Category.UNCATEGORIZED_ID) }
    if (included.isNotEmpty() && categoryIds.none { it in included }) return false
    return categoryIds.none { it in excluded }
}

internal fun isPastFetchWindow(nextUpdate: Long, window: FetchWindow): Boolean =
    nextUpdate in 1..<window.start

internal fun isAutoUpdateEligible(
    candidate: AutoUpdateCandidate,
    isFavorite: Boolean,
    context: EntryUpdateContext,
): Boolean {
    if (!isFavorite || !context.isInUpdateCategories || candidate.isCompleted) return false
    return evaluateAutoUpdateCandidate(candidate, context.restrictions, context.fetchWindow.end) == null
}

internal fun isEntryListOutdated(
    candidate: AutoUpdateCandidate,
    isFavorite: Boolean,
    context: EntryUpdateContext,
): Boolean = isPastFetchWindow(candidate.nextUpdate, context.fetchWindow) &&
    isAutoUpdateEligible(candidate, isFavorite, context)
