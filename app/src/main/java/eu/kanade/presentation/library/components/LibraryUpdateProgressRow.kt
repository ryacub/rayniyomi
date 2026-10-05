package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun LibraryUpdateProgressRow(
    progress: LibraryToolbarProgress,
    onClickCancelUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.small),
        ) {
            Text(
                text = stringResource(MR.strings.updating_library),
                style = MaterialTheme.typography.labelMedium,
            )
            if (progress.activeTitles.isNotEmpty()) {
                Text(
                    text = progress.activeTitles.joinToString(", "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = "${progress.completed}/${progress.total}",
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
            style = MaterialTheme.typography.labelMedium,
        )
        TextButton(onClick = onClickCancelUpdate) {
            Text(text = stringResource(MR.strings.action_cancel))
        }
    }
}

data class LibraryToolbarProgress(
    val activeTitles: List<String>,
    val completed: Int,
    val total: Int,
)

@PreviewLightDark
@Composable
private fun LibraryUpdateProgressRowPreview() {
    TachiyomiPreviewTheme {
        LibraryUpdateProgressRow(
            progress = LibraryToolbarProgress(
                activeTitles = listOf("A very long title that demonstrates truncation in the library update row"),
                completed = 2,
                total = 19,
            ),
            onClickCancelUpdate = {},
        )
    }
}
