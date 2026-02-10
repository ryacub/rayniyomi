package eu.kanade.presentation.category

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.category.anime.AnimeCategoryScreenState
import tachiyomi.domain.category.model.Category

/**
 * Anime-specific wrapper for the shared CategoryScreen composable.
 * Delegates to the shared implementation while maintaining the original API.
 */
@Composable
fun AnimeCategoryScreen(
    state: AnimeCategoryScreenState.Success,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
) {
    CategoryScreen(
        categories = state.categories,
        isEmpty = state.isEmpty,
        onClickCreate = onClickCreate,
        onClickRename = onClickRename,
        onClickHide = onClickHide,
        onClickDelete = onClickDelete,
        onChangeOrder = onChangeOrder,
    )
}
