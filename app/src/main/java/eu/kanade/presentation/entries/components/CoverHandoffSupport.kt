package eu.kanade.presentation.entries.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import eu.kanade.tachiyomi.ui.reader.CoverHandoff

/**
 * Tracks the detail cover for the reader handoff. While the reader carries the cover, the detail
 * cover is hidden, so the two do not show at once during the window fade.
 */
@Stable
class CoverHandoffState {
    /** The last window bounds of the detail cover, or null when the cover is not composed. */
    var bounds by mutableStateOf<Rect?>(null)

    var hidden by mutableStateOf(false)
        private set

    fun take(screenWidth: Int, screenHeight: Int, url: String?): CoverHandoff? =
        coverHandoffFor(bounds, screenWidth, screenHeight, url)?.also { hidden = true }

    fun reveal() {
        hidden = false
    }
}

val LocalCoverHandoff = compositionLocalOf { CoverHandoffState() }

fun Modifier.reportCoverBounds(): Modifier = composed {
    val state = LocalCoverHandoff.current
    DisposableEffect(state) {
        onDispose { state.bounds = null }
    }
    onGloballyPositioned { state.bounds = it.boundsInWindow() }
        .graphicsLayer { alpha = if (state.hidden) 0f else 1f }
}

/** Returns the cover handoff, or null when the cover is unknown, has no URL, or is off screen. */
fun coverHandoffFor(bounds: Rect?, screenWidth: Int, screenHeight: Int, url: String?): CoverHandoff? {
    if (bounds == null || url.isNullOrEmpty()) return null
    val screen = Rect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
    if (!bounds.overlaps(screen)) return null
    return CoverHandoff(bounds.left, bounds.top, bounds.width, bounds.height, url)
}
