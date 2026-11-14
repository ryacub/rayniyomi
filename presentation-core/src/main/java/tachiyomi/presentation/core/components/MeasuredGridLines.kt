package tachiyomi.presentation.core.components

import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class GridItemSample(val index: Int, val row: Int, val height: Int)

internal data class GridLayoutSample(
    val totalItems: Int,
    val maxSpan: Int,
    val items: List<GridItemSample>,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val viewportHeight: Int,
    val lineSpacing: Int,
)

internal data class GridScrollTarget(val index: Int, val scrollOffset: Int)

/** Measured line sizes drive both directions, so full-span items and tall headers cannot skew the thumb. */
internal class MeasuredGridLines {

    private data class Line(val firstIndex: Int, val itemCount: Int, val size: Int)

    private val measured = HashMap<Int, Line>()
    private var totalItems = 0
    private var maxSpan = 0
    private var lineSpacing = 0
    private var viewportHeight = 0
    private var firstVisibleRow = 0
    private var firstVisibleItemScrollOffset = 0
    private var estimatedLineSize = 0
    private var estimatedItemsPerLine = 1
    private var lineCount = 0

    fun record(sample: GridLayoutSample) {
        val visibleLines = sample.items.groupBy { it.row }.mapValues { (_, items) ->
            Line(
                firstIndex = items.minOf { it.index },
                itemCount = items.size,
                size = items.maxOf { it.height } + sample.lineSpacing,
            )
        }
        if (isNewLayout(sample, visibleLines)) measured.clear()
        measured.putAll(visibleLines)
        totalItems = sample.totalItems
        maxSpan = sample.maxSpan
        lineSpacing = sample.lineSpacing
        viewportHeight = sample.viewportHeight
        firstVisibleItemScrollOffset = sample.firstVisibleItemScrollOffset
        firstVisibleRow = sample.items.firstOrNull { it.index == sample.firstVisibleItemIndex }?.row
            ?: sample.items.firstOrNull()?.row
            ?: firstVisibleRow
        updateEstimates()
    }

    fun scrollProportion(): Float {
        val range = scrollRange()
        if (range <= 0) return 0f
        val position = lineStart(firstVisibleRow) + firstVisibleItemScrollOffset
        return (position.toFloat() / range).coerceIn(0f, 1f)
    }

    fun scrollTargetAt(proportion: Float): GridScrollTarget {
        if (lineCount == 0) return GridScrollTarget(index = 0, scrollOffset = 0)
        val target = (proportion.coerceIn(0f, 1f) * scrollRange().coerceAtLeast(0)).roundToInt()
        var line = 0
        var start = 0
        while (line < lineCount - 1 && start + sizeOf(line) <= target) {
            start += sizeOf(line)
            line++
        }
        return GridScrollTarget(index = firstIndexOf(line), scrollOffset = target - start)
    }

    private fun isNewLayout(sample: GridLayoutSample, visibleLines: Map<Int, Line>) =
        sample.totalItems != totalItems ||
            sample.maxSpan != maxSpan ||
            visibleLines.any { (row, line) -> measured[row]?.equals(line) == false }

    private fun updateEstimates() {
        if (measured.isEmpty()) {
            lineCount = 0
            return
        }
        estimatedLineSize = measured.values.mostCommon { it.size }
        estimatedItemsPerLine = measured.values.mostCommon { it.itemCount }.coerceAtLeast(1)
        val lastRow = measured.keys.max()
        val last = measured.getValue(lastRow)
        val itemsAfter = totalItems - (last.firstIndex + last.itemCount)
        lineCount = lastRow + 1 + ceil(itemsAfter.toFloat() / estimatedItemsPerLine).toInt()
    }

    private fun scrollRange() = lineStart(lineCount) - lineSpacing - viewportHeight

    private fun lineStart(line: Int) = (0..<line).sumOf { sizeOf(it) }

    private fun sizeOf(line: Int) = measured[line]?.size ?: estimatedLineSize

    private fun firstIndexOf(line: Int): Int {
        measured[line]?.let { return it.firstIndex }
        val nearestRow = measured.keys.filter { it < line }.maxOrNull() ?: measured.keys.min()
        val index = measured.getValue(nearestRow).firstIndex + (line - nearestRow) * estimatedItemsPerLine
        return index.coerceIn(0, totalItems - 1)
    }

    private fun Collection<Line>.mostCommon(value: (Line) -> Int): Int =
        groupingBy(value).eachCount().maxBy { it.value }.key
}
