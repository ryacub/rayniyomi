package eu.kanade.presentation.entries.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import eu.kanade.tachiyomi.ui.reader.CoverHandoff

/** Holds the last window bounds of the detail cover, or null when the cover is not composed. */
val LocalCoverBounds = compositionLocalOf<MutableState<Rect?>> { mutableStateOf(null) }

fun Modifier.reportCoverBounds(): Modifier = composed {
    val bounds = LocalCoverBounds.current
    DisposableEffect(bounds) {
        onDispose { bounds.value = null }
    }
    onGloballyPositioned { bounds.value = it.boundsInWindow() }
}

/** Returns the cover handoff, or null when the cover is unknown, has no URL, or is off screen. */
fun coverHandoffFor(bounds: Rect?, screenWidth: Int, screenHeight: Int, url: String?): CoverHandoff? {
    if (bounds == null || url.isNullOrEmpty()) return null
    val screen = Rect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
    if (!bounds.overlaps(screen)) return null
    return CoverHandoff(bounds.left, bounds.top, bounds.width, bounds.height, url)
}
