package eu.kanade.presentation.entries.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import eu.kanade.presentation.theme.TachiyomiPreviewTheme

@Composable
fun ResumeFabText(
    label: String,
    cue: String?,
    strokedCue: String?,
    onCueStroked: (String) -> Unit,
) {
    Column {
        Text(text = label)
        if (cue != null) {
            MarkedCue(
                cue = cue,
                alreadyStroked = cue == strokedCue,
                onStroked = { onCueStroked(cue) },
            )
        }
    }
}

@Composable
private fun MarkedCue(
    cue: String,
    alreadyStroked: Boolean,
    onStroked: () -> Unit,
) {
    val stroke = remember(cue) { Animatable(if (alreadyStroked) 1f else 0f) }
    LaunchedEffect(cue) {
        stroke.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
        onStroked()
    }
    val markColor = LocalContentColor.current.copy(alpha = 0.16f)
    Text(
        text = cue,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.drawBehind {
            val markHeight = size.height * 0.5f
            val markWidth = size.width * stroke.value
            val start = if (layoutDirection == LayoutDirection.Rtl) size.width - markWidth else 0f
            drawRoundRect(
                color = markColor,
                topLeft = Offset(start, size.height - markHeight),
                size = Size(markWidth, markHeight),
                cornerRadius = CornerRadius(markHeight / 2),
            )
        },
    )
}

@PreviewLightDark
@Composable
private fun ResumeFabTextPreview() {
    TachiyomiPreviewTheme {
        ResumeFabText(
            label = "Resume",
            cue = "Chapter 12 • Page: 8",
            strokedCue = "Chapter 12 • Page: 8",
            onCueStroked = {},
        )
    }
}
