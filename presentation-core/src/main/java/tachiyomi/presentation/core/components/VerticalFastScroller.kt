package tachiyomi.presentation.core.components

import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMaxBy
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws vertical fast scroller to a lazy list
 *
 * Set key with [STICKY_HEADER_KEY_PREFIX] prefix to any sticky header item in the list.
 */
@OptIn(FlowPreview::class)
@Composable
fun VerticalFastScroller(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = listState.layoutInfo
            val showScroller = layoutInfo.visibleItemsInfo.size < layoutInfo.totalItemsCount
            if (!showScroller) return@subcompose

            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            val scrolled = remember {
                MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx = contentHeight.toFloat() - thumbTopPadding -
                thumbBottomPadding - listState.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                val scrollRatio = (thumbOffsetY - thumbTopPadding) / trackHeightPx
                val scrollItem = layoutInfo.totalItemsCount * scrollRatio
                val scrollItemRounded = scrollItem.roundToInt()
                val scrollItemSize = layoutInfo.visibleItemsInfo.find { it.index == scrollItemRounded }?.size ?: 0
                val scrollItemOffset = scrollItemSize * (scrollItem - scrollItemRounded)
                listState.scrollToItem(
                    index = scrollItemRounded,
                    scrollOffset = scrollItemOffset.roundToInt(),
                )
                scrolled.tryEmit(Unit)
            }

            // When list scrolled
            LaunchedEffect(listState.firstVisibleItemScrollOffset) {
                if (listState.layoutInfo.totalItemsCount == 0 || isThumbDragged) return@LaunchedEffect
                val scrollOffset = computeScrollOffset(state = listState)
                val scrollRange = computeScrollRange(state = listState)
                val proportion = scrollOffset.toFloat() / (scrollRange.toFloat() - heightPx)
                thumbOffsetY = trackHeightPx * proportion + thumbTopPadding
                scrolled.tryEmit(Unit)
            }

            // Thumb alpha
            val alpha = remember { Animatable(0f) }
            val isThumbVisible = alpha.value > 0f
            LaunchedEffect(scrolled, alpha) {
                scrolled
                    .sample(100)
                    .collectLatest {
                        if (thumbAllowed()) {
                            alpha.snapTo(1f)
                            alpha.animateTo(0f, animationSpec = FadeOutAnimationSpec)
                        } else {
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (isThumbVisible && !listState.isScrollInProgress) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        thumbTopPadding,
                                        thumbTopPadding + trackHeightPx,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (isThumbVisible && !isThumbDragged && !listState.isScrollInProgress) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(horizontal = 8.dp)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness)
                    .alpha(alpha.value)
                    .background(color = thumbColor, shape = ThumbShape),
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun VerticalGridFastScroller(
    state: LazyGridState,
    modifier: Modifier = Modifier,
    thumbAllowed: () -> Boolean = { true },
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    topContentPadding: Dp = Dp.Hairline,
    bottomContentPadding: Dp = Dp.Hairline,
    endContentPadding: Dp = Dp.Hairline,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val contentPlaceable = subcompose("content", content).map { it.measure(constraints) }
        val contentHeight = contentPlaceable.fastMaxBy { it.height }?.height ?: 0
        val contentWidth = contentPlaceable.fastMaxBy { it.width }?.width ?: 0

        val scrollerConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val scrollerPlaceable = subcompose("scroller") {
            val layoutInfo = state.layoutInfo
            val showScroller = layoutInfo.visibleItemsInfo.size < layoutInfo.totalItemsCount
            if (!showScroller) return@subcompose
            val thumbTopPadding = with(LocalDensity.current) { topContentPadding.toPx() }
            var thumbOffsetY by remember(thumbTopPadding) { mutableFloatStateOf(thumbTopPadding) }

            val dragInteractionSource = remember { MutableInteractionSource() }
            val isThumbDragged by dragInteractionSource.collectIsDraggedAsState()
            val scrolled = remember {
                MutableSharedFlow<Unit>(
                    extraBufferCapacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
            }

            val thumbBottomPadding = with(LocalDensity.current) { bottomContentPadding.toPx() }
            val heightPx =
                contentHeight.toFloat() - thumbTopPadding - thumbBottomPadding - state.layoutInfo.afterContentPadding
            val thumbHeightPx = with(LocalDensity.current) { ThumbLength.toPx() }
            val trackHeightPx = heightPx - thumbHeightPx

            val lines = remember { MeasuredGridLines() }

            // When thumb dragged
            LaunchedEffect(thumbOffsetY) {
                if (state.layoutInfo.totalItemsCount == 0 || !isThumbDragged) return@LaunchedEffect
                lines.record(state.layoutSample())
                val target = lines.scrollTargetAt((thumbOffsetY - thumbTopPadding) / trackHeightPx)
                state.scrollToItem(index = target.index, scrollOffset = target.scrollOffset)
                scrolled.tryEmit(Unit)
            }

            // When list scrolled
            LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
                if (state.layoutInfo.totalItemsCount == 0 || isThumbDragged) return@LaunchedEffect
                lines.record(state.layoutSample())
                thumbOffsetY = trackHeightPx * lines.scrollProportion() + thumbTopPadding
                scrolled.tryEmit(Unit)
            }

            // Thumb alpha
            val alpha = remember { Animatable(0f) }
            val isThumbVisible = alpha.value > 0f
            LaunchedEffect(scrolled, alpha) {
                scrolled
                    .sample(100)
                    .collectLatest {
                        if (thumbAllowed()) {
                            alpha.snapTo(1f)
                            alpha.animateTo(0f, animationSpec = FadeOutAnimationSpec)
                        } else {
                            alpha.animateTo(0f, animationSpec = ImmediateFadeOutAnimationSpec)
                        }
                    }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                    .then(
                        // Recompose opts
                        if (isThumbVisible && !state.isScrollInProgress) {
                            Modifier.draggable(
                                interactionSource = dragInteractionSource,
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    val newOffsetY = thumbOffsetY + delta
                                    thumbOffsetY = newOffsetY.coerceIn(
                                        thumbTopPadding,
                                        thumbTopPadding + trackHeightPx,
                                    )
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        // Exclude thumb from gesture area only when needed
                        if (isThumbVisible && !isThumbDragged && !state.isScrollInProgress) {
                            Modifier.systemGestureExclusion()
                        } else {
                            Modifier
                        },
                    )
                    .height(ThumbLength)
                    .padding(end = endContentPadding)
                    .width(ThumbThickness)
                    .alpha(alpha.value)
                    .background(color = thumbColor, shape = ThumbShape),
            )
        }.map { it.measure(scrollerConstraints) }
        val scrollerWidth = scrollerPlaceable.fastMaxBy { it.width }?.width ?: 0

        layout(contentWidth, contentHeight) {
            contentPlaceable.fastForEach {
                it.place(0, 0)
            }
            scrollerPlaceable.fastForEach {
                it.placeRelative(contentWidth - scrollerWidth, 0)
            }
        }
    }
}

private fun LazyGridState.layoutSample(): GridLayoutSample {
    val info = layoutInfo
    return GridLayoutSample(
        totalItems = info.totalItemsCount,
        maxSpan = info.maxSpan,
        items = info.visibleItemsInfo
            .filter { it.row != LazyGridItemInfo.UnknownRow }
            .map { GridItemSample(index = it.index, row = it.row, height = it.size.height) },
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        viewportHeight = info.viewportSize.height - info.beforeContentPadding - info.afterContentPadding,
        lineSpacing = info.mainAxisItemSpacing,
    )
}

private fun computeScrollOffset(state: LazyListState): Int {
    if (state.layoutInfo.totalItemsCount == 0) return 0
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val startChild = visibleItems
        .fastFirstOrNull { (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true }!!
    val endChild = visibleItems.last()
    val minPosition = min(startChild.index, endChild.index)
    val maxPosition = max(startChild.index, endChild.index)
    val itemsBefore = minPosition.coerceAtLeast(0)
    val startDecoratedTop = startChild.top
    val laidOutArea = abs(endChild.bottom - startDecoratedTop)
    val itemRange = abs(minPosition - maxPosition) + 1
    val avgSizePerRow = laidOutArea.toFloat() / itemRange
    return (itemsBefore * avgSizePerRow + (0 - startDecoratedTop)).roundToInt()
}

private fun computeScrollRange(state: LazyListState): Int {
    if (state.layoutInfo.totalItemsCount == 0) return 0
    val visibleItems = state.layoutInfo.visibleItemsInfo
    val startChild = visibleItems
        .fastFirstOrNull { (it.key as? String)?.startsWith(STICKY_HEADER_KEY_PREFIX)?.not() ?: true }!!
    val endChild = visibleItems.last()
    val laidOutArea = endChild.bottom - startChild.top
    val laidOutRange = abs(startChild.index - endChild.index) + 1
    return (laidOutArea.toFloat() / laidOutRange * state.layoutInfo.totalItemsCount).roundToInt()
}

object Scroller {
    const val STICKY_HEADER_KEY_PREFIX = "sticky:"
}

private val ThumbLength = 48.dp
private val ThumbThickness = 12.dp
private val ThumbShape = RoundedCornerShape(ThumbThickness / 2)
private val FadeOutAnimationSpec = tween<Float>(
    durationMillis = ViewConfiguration.getScrollBarFadeDuration(),
    delayMillis = 2000,
)
private val ImmediateFadeOutAnimationSpec = tween<Float>(
    durationMillis = ViewConfiguration.getScrollBarFadeDuration(),
)

private val LazyListItemInfo.top: Int
    get() = offset

private val LazyListItemInfo.bottom: Int
    get() = offset + size
