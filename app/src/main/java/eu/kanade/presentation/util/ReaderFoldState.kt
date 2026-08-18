package eu.kanade.presentation.util

import androidx.compose.runtime.Immutable
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo

/**
 * The fold that the reader sees. It is null on a phone and on a tablet that
 * does not fold.
 */
@Immutable
data class ReaderFoldState(
    val orientation: FoldOrientation,
    val state: FoldState,
    val occlusionType: FoldOcclusionType,
    val bounds: FoldBounds,
)

/** The axis of the fold. */
enum class FoldOrientation {
    Vertical,
    Horizontal,
}

/** How far the device is open. */
enum class FoldState {
    Flat,
    HalfOpen,
}

/** How much content the fold hides. */
enum class FoldOcclusionType {
    None,
    Full,
}

/**
 * The fold rectangle in window pixels. The caller converts to dp.
 */
@Immutable
data class FoldBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left

    val height: Int get() = bottom - top
}

/** Maps the first fold in [layoutInfo]. Returns null when the window has no fold. */
fun readerFoldStateFrom(layoutInfo: WindowLayoutInfo): ReaderFoldState? =
    layoutInfo.displayFeatures
        .filterIsInstance<FoldingFeature>()
        .firstOrNull()
        ?.let(::foldStateFrom)

/**
 * Maps [feature] to reader state. Returns null when androidx reports a value
 * that this version does not recognise.
 */
private fun foldStateFrom(feature: FoldingFeature): ReaderFoldState? {
    val orientation = when (feature.orientation) {
        FoldingFeature.Orientation.VERTICAL -> FoldOrientation.Vertical
        FoldingFeature.Orientation.HORIZONTAL -> FoldOrientation.Horizontal
        else -> return null
    }
    val state = when (feature.state) {
        FoldingFeature.State.FLAT -> FoldState.Flat
        FoldingFeature.State.HALF_OPENED -> FoldState.HalfOpen
        else -> return null
    }
    val occlusionType = when (feature.occlusionType) {
        FoldingFeature.OcclusionType.NONE -> FoldOcclusionType.None
        FoldingFeature.OcclusionType.FULL -> FoldOcclusionType.Full
        else -> return null
    }
    return ReaderFoldState(
        orientation = orientation,
        state = state,
        occlusionType = occlusionType,
        bounds = FoldBounds(
            left = feature.bounds.left,
            top = feature.bounds.top,
            right = feature.bounds.right,
            bottom = feature.bounds.bottom,
        ),
    )
}
