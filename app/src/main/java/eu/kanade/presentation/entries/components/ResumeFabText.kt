package eu.kanade.presentation.entries.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme

@Composable
fun ResumeFabText(
    label: String,
    cue: String?,
) {
    Column {
        Text(text = label)
        if (cue != null) {
            Text(
                text = cue,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ResumeFabTextPreview() {
    TachiyomiPreviewTheme {
        ResumeFabText(
            label = "Resume",
            cue = "Chapter 12",
        )
    }
}
