package eu.kanade.tachiyomi.data.library

import tachiyomi.domain.category.model.Category

data class AutoUpdatePolicy(
    val restrictions: Set<String>,
    val isInUpdateCategories: Boolean,
    val fetchWindow: Pair<Long, Long>,
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

// A manual refresh keeps a nextUpdate inside the fetch window, so only one before the window is overdue.
internal fun isEntryListOutdated(
    candidate: AutoUpdateCandidate,
    isFavorite: Boolean,
    policy: AutoUpdatePolicy,
): Boolean {
    if (!isFavorite || !policy.isInUpdateCategories || candidate.isCompleted) return false
    val (windowStart, windowEnd) = policy.fetchWindow
    if (candidate.nextUpdate <= 0L || candidate.nextUpdate >= windowStart) return false
    return evaluateAutoUpdateCandidate(candidate, policy.restrictions, windowEnd) == null
}
