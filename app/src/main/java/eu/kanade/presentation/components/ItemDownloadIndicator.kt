package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource

internal fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onLongClick = {
        onLongClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    onClick = onClick,
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

internal val IndicatorSize = 26.dp
internal val IndicatorPadding = 2.dp

// To match composable parameter name when used later
internal val IndicatorStrokeWidth = IndicatorPadding

internal val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
internal val ArrowModifier = Modifier
    .size(IndicatorSize - 7.dp)

@Composable
internal fun ActiveDownloadMark(queued: Boolean, paused: Boolean, progress: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        paused -> Icon(
            imageVector = Icons.Outlined.PauseCircleOutline,
            contentDescription = stringResource(MR.strings.download_notifier_download_paused),
            modifier = Modifier.size(IndicatorSize),
            tint = color,
        )
        queued -> Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = stringResource(MR.strings.download_indicator_queued),
            modifier = Modifier.size(IndicatorSize),
            tint = color,
        )
        progress < 0 -> CircularProgressIndicator(
            modifier = Modifier.size(IndicatorSize),
            color = color,
            strokeWidth = 2.dp,
        )
        else -> DownloadProgressRing(progress.coerceIn(0, 100))
    }
}

@Composable
internal fun UpdatesDownloadMark(queued: Boolean, progress: Int) {
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val arrowColor = if (queued || progress == 0) {
        CircularProgressIndicator(
            modifier = IndicatorModifier,
            color = strokeColor,
            strokeWidth = IndicatorStrokeWidth,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
        )
        strokeColor
    } else {
        val animatedProgress by animateFloatAsState(
            targetValue = progress / 100f,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "progress",
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = IndicatorModifier,
            color = strokeColor,
            strokeWidth = IndicatorSize / 2,
            trackColor = Color.Transparent,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
        if (animatedProgress < 0.5f) strokeColor else MaterialTheme.colorScheme.background
    }
    Icon(
        imageVector = Icons.Outlined.ArrowDownward,
        contentDescription = null,
        modifier = ArrowModifier,
        tint = arrowColor,
    )
}

@Composable
private fun DownloadProgressRing(progress: Int) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(30.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            strokeWidth = 2.dp,
            gapSize = 0.dp,
        )
        Text(
            text = stringResource(MR.strings.download_indicator_percent, progress),
            fontSize = with(LocalDensity.current) { 9.dp.toSp() },
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
internal fun downloadCompletionScale(downloaded: Boolean): State<Float> {
    val transition = updateTransition(downloaded, label = "download completion")
    return transition.animateFloat(
        transitionSpec = { tween(durationMillis = 180) },
        label = "checkmark scale",
    ) { complete -> if (complete) 1f else 0.8f }
}

internal val preferences: DownloadPreferences by lazy { appGraph.downloadPreferences }
