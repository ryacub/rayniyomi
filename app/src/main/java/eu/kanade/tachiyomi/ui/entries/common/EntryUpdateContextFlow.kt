package eu.kanade.tachiyomi.ui.entries.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import eu.kanade.tachiyomi.data.library.EntryUpdateContext
import eu.kanade.tachiyomi.data.library.FetchWindow
import eu.kanade.tachiyomi.data.library.isInAutoUpdateCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.ZonedDateTime

fun fetchWindowFlow(
    getWindow: (ZonedDateTime) -> Pair<Long, Long>,
    clock: Clock,
    lifecycle: Lifecycle,
): Flow<FetchWindow> = flow { emit(FetchWindow.from(getWindow(ZonedDateTime.now(clock)))) }
    .flowWithLifecycle(lifecycle)

fun entryUpdateContextFlow(
    restrictions: Flow<Set<String>>,
    includedCategories: Flow<Set<String>>,
    excludedCategories: Flow<Set<String>>,
    entryCategoryIds: Flow<List<Long>>,
    fetchWindow: Flow<FetchWindow>,
): Flow<EntryUpdateContext> = combine(
    restrictions,
    includedCategories,
    excludedCategories,
    entryCategoryIds,
    fetchWindow,
) { restrictionSet, included, excluded, categoryIds, window ->
    EntryUpdateContext(
        restrictions = restrictionSet,
        isInUpdateCategories = isInAutoUpdateCategories(
            entryCategoryIds = categoryIds,
            included = included.mapTo(HashSet()) { it.toLong() },
            excluded = excluded.mapTo(HashSet()) { it.toLong() },
        ),
        fetchWindow = window,
    )
}
