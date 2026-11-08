package eu.kanade.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import kotlin.math.roundToInt

/** Emphasized ease: a quick start, a long settle onto the full page. */
private val ExpandEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Moves the cover frame from its position on the title screen to the centre of the screen,
 * then expands it to fill the reader. The image keeps its aspect ratio while the frame moves
 * and grows. The cover fades out once [finished] becomes true, which reveals the first page.
 */
@Composable
fun ReaderCoverOverlay(
    handoff: CoverHandoff,
    finished: Boolean,
    onFinished: () -> Unit,
) {
    // move: cover travels from its position to the centre. expand: centre frame grows to fill.
    val move = remember { Animatable(0f) }
    val expand = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) {
        move.animateTo(1f, tween(durationMillis = 280, easing = ExpandEasing))
        expand.animateTo(1f, tween(durationMillis = 380, easing = ExpandEasing))
    }
    LaunchedEffect(finished) {
        if (finished) {
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
                drawRect(Color.Black, alpha = move.value)
            },
    ) {
        if (screen != IntSize.Zero) {
            AsyncImage(
                model = handoff.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, _ ->
                        // Read the progress here, in layout, so each frame skips recomposition.
                        val m = move.value
                        val e = expand.value
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
