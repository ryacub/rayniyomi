package eu.kanade.presentation.entries.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ResumeFabText(canResume: Boolean, detail: String? = null) {
    val label = when {
        !canResume -> stringResource(MR.strings.action_start)
        detail == null -> stringResource(MR.strings.action_resume)
        else -> stringResource(MR.strings.resume_with_detail, stringResource(MR.strings.action_resume), detail)
    }
    Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@PreviewLightDark
@Composable
private fun ResumeFabTextPreview() {
    TachiyomiPreviewTheme {
        ResumeFabText(canResume = true, detail = "S02E05")
    }
}
