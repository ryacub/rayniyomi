package eu.kanade.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.reader.CoverHandoff
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class CoverMotionPhases(val move: Float, val expand: Float)

/**
 * The part of one motion curve that the move to the centre uses. The move and the expansion
 * split the curve by the distance each covers, so the cover keeps its speed where they join.
 */
internal fun coverMoveShare(handoff: CoverHandoff, screen: IntSize): Float {
    val centreX = (screen.width - handoff.width) / 2f
    val centreY = (screen.height - handoff.height) / 2f
    val move = hypot(centreX - handoff.left, centreY - handoff.top)
    val expand = hypot(centreX, centreY)
    return if (move + expand == 0f) 0f else move / (move + expand)
}

internal fun coverMotionPhases(progress: Float, moveShare: Float): CoverMotionPhases {
    val move = if (moveShare <= 0f) 1f else (progress / moveShare).coerceIn(0f, 1f)
    val expand = if (moveShare >= 1f) 0f else ((progress - moveShare) / (1f - moveShare)).coerceIn(0f, 1f)
    return CoverMotionPhases(move, expand)
}

/**
 * Moves the cover frame from its position on the title screen to the centre of the screen,
 * then expands it to fill the reader. The image keeps its aspect ratio while the frame moves
 * and grows. The cover fades out after the expansion ends and [finished] becomes true, which
 * reveals the first page.
 */
@Composable
fun ReaderCoverOverlay(
    handoff: CoverHandoff,
    finished: Boolean,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 640, easing = FastOutSlowInEasing))
        expanded = true
    }
    LaunchedEffect(finished, expanded) {
        if (finished && expanded) {
            fade.animateTo(0f, tween(durationMillis = 300, easing = LinearOutSlowInEasing))
            onFinished()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screen = it }
            .graphicsLayer { alpha = fade.value }
            .drawBehind {
                // Hide the reader beneath, so no blank page shows around the moving cover.
                val phases = coverMotionPhases(progress.value, coverMoveShare(handoff, screen))
                drawRect(Color.Black, alpha = phases.move)
            },
    ) {
        if (screen != IntSize.Zero) {
            val moveShare = remember(screen) { coverMoveShare(handoff, screen) }
            AsyncImage(
                model = handoff.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, _ ->
                        // Read the progress here, in layout, so each frame skips recomposition.
                        val (m, e) = coverMotionPhases(progress.value, moveShare)
                        val centreX = (screen.width - handoff.width) / 2f
                        val centreY = (screen.height - handoff.height) / 2f
                        val midX = lerp(handoff.left, centreX, m)
                        val midY = lerp(handoff.top, centreY, m)
                        val width = lerp(handoff.width, screen.width.toFloat(), e).roundToInt()
                        val height = lerp(handoff.height, screen.height.toFloat(), e).roundToInt()
                        val x = lerp(midX, 0f, e).roundToInt()
                        val y = lerp(midY, 0f, e).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(screen.width, screen.height) { placeable.place(x, y) }
                    },
            )
        }
    }
}
