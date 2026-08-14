package eu.kanade.presentation.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun UpdatesCategoryFilterDialog(
    categories: List<Category>,
    included: Set<String>,
    excluded: Set<String>,
    detailsText: String,
    onCycleCategory: (Category) -> Unit,
    onDismissRequest: () -> Unit,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(stringResource(MR.strings.categories)),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = TabbedDialogPaddings.Horizontal,
                    vertical = TabbedDialogPaddings.Vertical,
                ),
        ) {
            Text(
                text = detailsText,
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))
            categories.forEach { category ->
                val label = category.visualName
                val state = when (category.id.toString()) {
                    in included -> TriState.ENABLED_IS
                    in excluded -> TriState.ENABLED_NOT
                    else -> TriState.DISABLED
                }
                Box(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$label, ${state.stateLabel()}"
                    },
                ) {
                    TriStateItem(
                        label = label,
                        state = state,
                        onClick = { onCycleCategory(category) },
                    )
                }
            }
        }
    }
}

private fun TriState.stateLabel(): String = when (this) {
    TriState.ENABLED_IS -> "Included"
    TriState.ENABLED_NOT -> "Excluded"
    TriState.DISABLED -> "Not filtered"
}

@PreviewLightDark
@Composable
private fun UpdatesCategoryFilterDialogPreview() {
    TachiyomiPreviewTheme {
        UpdatesCategoryFilterDialog(
            categories = listOf(
                Category(id = 0, name = "", order = -1, flags = 0, hidden = false),
                Category(id = 1, name = "Horror", order = 0, flags = 0, hidden = false),
                Category(id = 2, name = "Romance", order = 1, flags = 0, hidden = false),
            ),
            included = setOf("1"),
            excluded = setOf("2"),
            detailsText = "Updates in excluded categories will not be shown " +
                "even if they are also in any included categories.",
            onCycleCategory = {},
            onDismissRequest = {},
        )
    }
}
