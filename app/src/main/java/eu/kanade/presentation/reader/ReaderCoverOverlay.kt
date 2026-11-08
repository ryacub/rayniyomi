package eu.kanade.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.reader.CoverHandoff

/**
 * Expands the cover from its position on the title screen to the full reader. The cover fades
 * out once [finished] becomes true, which reveals the first page underneath.
 */
@Composable
fun ReaderCoverOverlay(
    handoff: CoverHandoff,
    finished: Boolean,
    onFinished: () -> Unit,
) {
    val expand = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(Unit) {
        expand.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(finished) {
        if (finished) {
            fade.animateTo(0f, tween(durationMillis = 250))
            onFinished()
        }
    }
    val progress = expand.value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screen = it }
            .graphicsLayer { alpha = fade.value },
    ) {
        if (screen != IntSize.Zero) {
            AsyncImage(
                model = handoff.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = lerp(handoff.width / screen.width, 1f, progress)
                        scaleY = lerp(handoff.height / screen.height, 1f, progress)
                        translationX = lerp(handoff.left, 0f, progress)
                        translationY = lerp(handoff.top, 0f, progress)
                    },
            )
        }
    }
}
