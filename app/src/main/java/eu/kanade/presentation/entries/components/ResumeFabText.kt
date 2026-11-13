package eu.kanade.presentation.entries.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ResumeFabText(canResume: Boolean) {
    val label = if (canResume) MR.strings.action_resume else MR.strings.action_start
    Text(text = stringResource(label))
}

@PreviewLightDark
@Composable
private fun ResumeFabTextPreview() {
    TachiyomiPreviewTheme {
        ResumeFabText(canResume = true)
    }
}
