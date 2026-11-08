package eu.kanade.presentation.entries.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA

@Composable
fun ItemProgressText(text: String, isResumeTarget: Boolean) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (isResumeTarget) {
            MaterialTheme.colorScheme.primary
        } else {
            LocalContentColor.current.copy(alpha = DISABLED_ALPHA)
        },
        fontWeight = if (isResumeTarget) FontWeight.SemiBold else null,
    )
}
