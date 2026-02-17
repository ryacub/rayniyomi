package eu.kanade.tachiyomi.ui.entries.common

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.domain.category.model.Category

internal object EntryCategoryActions {
    fun buildInitialSelection(
        categories: List<Category>,
        selectedCategoryIds: List<Long>,
    ): ImmutableList<CheckboxState<Category>> {
        return categories
            .mapAsCheckboxState { it.id in selectedCategoryIds }
            .toImmutableList()
    }
}
