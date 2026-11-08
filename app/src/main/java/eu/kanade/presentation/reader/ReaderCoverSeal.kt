package eu.kanade.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Covers the reader until the first page is ready. A translucent sheet peels back from the
 * opening edge, as unwrapped cellophane does: a rolled edge travels across the page and the
 * sheet crinkles as it moves. The end edge is the opening edge for right-to-left reading.
 * The whole layer fades out once [finished] becomes true.
 */
@Composable
fun ReaderCoverSeal(
    finished: Boolean,
    atEnd: Boolean,
    ready: Boolean,
    onFinished: () -> Unit,
) {
    val intro = remember { Animatable(0f) }
    val peel = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, tween(durationMillis = 120, easing = LinearEasing))
    }
    LaunchedEffect(ready) {
        if (ready) {
            peel.animateTo(1f, tween(durationMillis = 700, easing = PeelEasing))
        }
    }
    LaunchedEffect(finished) {
        if (finished) {
            fade.animateTo(0f, tween(durationMillis = 300, easing = LinearEasing))
            onFinished()
        }
    }
    val sheetColor = MaterialTheme.colorScheme.surface
    val rollColor = MaterialTheme.colorScheme.onSurface
    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value * intro.value }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform(
                {
                    if (atEnd) {
                        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(size.width / 2f, size.height / 2f))
                    }
                },
                {
                    drawPeelingSheet(progress = peel.value, sheetColor = sheetColor, rollColor = rollColor)
                },
            )
        }
        if (!finished) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Starts gently, runs quickly through the middle, and settles without a hard stop. */
private val PeelEasing: Easing = CubicBezierEasing(0.33f, 0f, 0.15f, 1f)

/**
 * Draws the sheet from the revealed boundary to the far edge. The boundary sits at
 * [progress] of the width, and a rolled band marks the free edge.
 */
private fun DrawScope.drawPeelingSheet(progress: Float, sheetColor: Color, rollColor: Color) {
    val width = size.width
    val height = size.height
    val edge = width * progress.coerceIn(0f, 1f)
    if (edge >= width) return

    drawRect(
        color = sheetColor.copy(alpha = 0.94f),
        topLeft = Offset(edge, 0f),
        size = Size(width - edge, height),
    )

    val creases = 6
    val creaseWidth = 1.dp.toPx()
    for (i in 1..creases) {
        val x = edge + (width - edge) * i / (creases + 1)
        drawLine(
            color = Color.White.copy(alpha = 0.08f),
            start = Offset(x, 0f),
            end = Offset(x - height * 0.12f, height),
            strokeWidth = creaseWidth,
        )
    }

    val shadowWidth = 16.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
            startX = edge,
            endX = edge + shadowWidth,
        ),
        topLeft = Offset(edge, 0f),
        size = Size(shadowWidth, height),
    )

    val rollRadius = 18.dp.toPx()
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                rollColor.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.55f),
                rollColor.copy(alpha = 0.14f),
            ),
            startX = edge - rollRadius,
            endX = edge + rollRadius,
        ),
        topLeft = Offset(edge - rollRadius, 0f),
        size = Size(rollRadius * 2, height),
    )
}
