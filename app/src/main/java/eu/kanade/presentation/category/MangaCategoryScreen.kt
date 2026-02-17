package eu.kanade.presentation.category

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.category.manga.MangaCategoryScreenState
import tachiyomi.domain.category.model.Category

/**
 * Manga-specific wrapper for the shared CategoryScreen composable.
 * Delegates to the shared implementation while maintaining the original API.
 */
@Composable
fun MangaCategoryScreen(
    state: MangaCategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
) {
    CategoryScreen(
        categories = state.categories,
        alphabeticalSortEnabled = state.alphabeticalSortEnabled,
        isEmpty = state.isEmpty,
        onClickCreate = onClickCreate,
        onClickRename = onClickRename,
        onClickHide = onClickHide,
        onClickDelete = onClickDelete,
        onChangeOrder = onChangeOrder,
    )
}
