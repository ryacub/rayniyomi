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

internal fun isEntryListOutdated(
    candidate: AutoUpdateCandidate,
    isFavorite: Boolean,
    policy: AutoUpdatePolicy,
): Boolean {
    if (!isFavorite || !policy.isInUpdateCategories || candidate.isCompleted) return false
    if (!isBeforeFetchWindow(candidate.nextUpdate, policy.fetchWindow)) return false
    return evaluateAutoUpdateCandidate(candidate, policy.restrictions, policy.fetchWindow.second) == null
}

private fun isBeforeFetchWindow(nextUpdate: Long, fetchWindow: Pair<Long, Long>): Boolean =
    nextUpdate in 1..<fetchWindow.first
