package eu.kanade.presentation.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Covers the reader until the first page is ready. A translucent strip lifts from the
 * opening edge, then the cover fades out once [finished] becomes true.
 */
@Composable
fun ReaderCoverSeal(
    finished: Boolean,
    onFinished: () -> Unit,
) {
    val lift = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        lift.animateTo(1f, tween(durationMillis = 300, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(finished) {
        if (finished) {
            fade.animateTo(0f, tween(durationMillis = 200))
            onFinished()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.25f * lift.value)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        )
        if (!finished) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
