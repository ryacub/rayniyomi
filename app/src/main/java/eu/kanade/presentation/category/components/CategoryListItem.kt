package eu.kanade.presentation.category.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    title: String,
    showDragHandle: Boolean,
    isChild: Boolean,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CategoryListItemContent(
        category = category,
        title = title,
        showDragHandle = showDragHandle,
        isChild = isChild,
        onRename = onRename,
        onHide = onHide,
        onDelete = onDelete,
        dragHandleModifier = Modifier.draggableHandle(),
        modifier = modifier,
    )
}

@Composable
private fun CategoryListItemContent(
    category: Category,
    title: String,
    showDragHandle: Boolean,
    isChild: Boolean,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small)
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isChild) {
                Spacer(modifier = Modifier.width(MaterialTheme.padding.medium))
            }
            if (showDragHandle) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(MaterialTheme.padding.medium)
                        .then(dragHandleModifier),
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_category),
                )
            }
            IconButton(
                onClick = onHide,
                content = {
                    Icon(
                        imageVector = if (category.hidden) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = stringResource(AYMR.strings.action_hide),
                    )
                },
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CategoryListItemPreview() {
    TachiyomiPreviewTheme {
        CategoryListItemContent(
            category = Category(
                id = 1,
                name = "Isekai",
                order = 0,
                flags = 0,
                hidden = false,
                parentId = 2,
            ),
            title = "Action / Isekai",
            showDragHandle = true,
            isChild = true,
            onRename = {},
            onHide = {},
            onDelete = {},
            dragHandleModifier = Modifier,
        )
    }
}
