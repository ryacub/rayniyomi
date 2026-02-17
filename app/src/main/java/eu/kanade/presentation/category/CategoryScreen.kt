package eu.kanade.presentation.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.category.components.CategoryFloatingActionButton
import eu.kanade.presentation.category.components.CategoryListItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.flattenForDisplay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen

/**
 * Shared category screen composable for both anime and manga categories.
 * Consolidates duplicated UI logic that was previously maintained in parallel files.
 */
@Composable
fun CategoryScreen(
    categories: ImmutableList<Category>,
    alphabeticalSortEnabled: Boolean,
    isEmpty: Boolean,
    onClickCreate: () -> Unit,
    onClickRename: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    Scaffold(
        floatingActionButton = {
            CategoryFloatingActionButton(
                lazyListState = lazyListState,
                onCreate = onClickCreate,
            )
        },
    ) { paddingValues ->
        if (isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_category,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        CategoryContent(
            categories = categories,
            alphabeticalSortEnabled = alphabeticalSortEnabled,
            lazyListState = lazyListState,
            paddingValues = paddingValues,
            onClickRename = onClickRename,
            onClickHide = onClickHide,
            onClickDelete = onClickDelete,
            onChangeOrder = onChangeOrder,
        )
    }
}

@Composable
private fun CategoryContent(
    categories: ImmutableList<Category>,
    alphabeticalSortEnabled: Boolean,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onClickRename: (Category) -> Unit,
    onClickHide: (Category) -> Unit,
    onClickDelete: (Category) -> Unit,
    onChangeOrder: (Category, Int) -> Unit,
) {
    val context = LocalContext.current
    val categoriesState = remember { categories.flattenForDisplay().toMutableStateList() }
    var pendingCategoriesStateSync by remember { mutableStateOf<List<Category>?>(null) }
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        if (alphabeticalSortEnabled) return@rememberReorderableLazyListState
        val item = categoriesState.removeAt(from.index)
        categoriesState.add(to.index, item)
        onChangeOrder(item, to.index)
    }

    LaunchedEffect(categories) {
        val flattenedCategories = categories.flattenForDisplay()
        if (!reorderableState.isAnyItemDragging) {
            categoriesState.clear()
            categoriesState.addAll(flattenedCategories)
            pendingCategoriesStateSync = null
        } else {
            pendingCategoriesStateSync = flattenedCategories
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            pendingCategoriesStateSync?.let { pending ->
                categoriesState.clear()
                categoriesState.addAll(pending)
                pendingCategoriesStateSync = null
            }
        }
    }

    val categoryById = remember(categoriesState) { categoriesState.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = lazyListState,
        contentPadding = PaddingValues(MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = categoriesState,
            key = { category -> category.key },
        ) { category ->
            ReorderableItem(reorderableState, category.key) {
                CategoryListItem(
                    modifier = Modifier.animateItem(),
                    category = category,
                    title = category.visualName(categoryById, context),
                    showDragHandle = !alphabeticalSortEnabled,
                    isChild = category.parentId != null,
                    onRename = { onClickRename(category) },
                    onHide = { onClickHide(category) },
                    onDelete = { onClickDelete(category) },
                )
            }
        }
    }
}

private val Category.key inline get() = "category-$id"

@PreviewLightDark
@Composable
private fun CategoryScreenPreview() {
    TachiyomiPreviewTheme {
        CategoryScreen(
            categories = listOf(
                Category(id = 1, name = "Action", order = 0, flags = 0, hidden = false, parentId = null),
                Category(id = 2, name = "Isekai", order = 1, flags = 0, hidden = false, parentId = 1),
            ).toImmutableList(),
            alphabeticalSortEnabled = false,
            isEmpty = false,
            onClickCreate = {},
            onClickRename = {},
            onClickHide = {},
            onClickDelete = {},
            onChangeOrder = { _, _ -> },
        )
    }
}
