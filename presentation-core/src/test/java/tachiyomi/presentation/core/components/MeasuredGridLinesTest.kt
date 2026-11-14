package tachiyomi.presentation.core.components

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

class MeasuredGridLinesTest {

    @Test
    fun `dragging the thumb down moves the anime list forward in even steps`() {
        assertDragDownIsEven(animeGrid())
    }

    @Test
    fun `dragging the thumb down moves the library grid forward in even steps`() {
        assertDragDownIsEven(libraryGrid())
    }

    @Test
    fun `full thumb travel reaches the end of the list and returns to the top`() {
        val grid = animeGrid()
        val lines = MeasuredGridLines()

        grid.dragTo(lines, 1f)
        grid.position shouldBe grid.maxScroll

        grid.dragTo(lines, 0f)
        grid.position shouldBe 0
    }

    @Test
    fun `thumb position read back after each drag step matches the drag`() {
        for (grid in listOf(animeGrid(), libraryGrid())) {
            val lines = MeasuredGridLines()
            for (step in 0..DRAG_STEPS) {
                val proportion = step.toFloat() / DRAG_STEPS
                grid.dragTo(lines, proportion)
                lines.record(grid.sample())

                (abs(lines.scrollProportion() - proportion) < 0.001f) shouldBe true
            }
        }
    }

    @Test
    fun `dragging up from mid list with an unmeasured header reaches the top`() {
        val grid = animeGrid()
        grid.scrollTo(GridScrollTarget(index = 30, scrollOffset = 0))
        val lines = MeasuredGridLines()
        lines.record(grid.sample())
        val start = lines.scrollProportion()
        var previous = grid.position
        var largestWrongWayStep = 0

        for (step in 0..DRAG_STEPS) {
            grid.dragTo(lines, start * (1f - step.toFloat() / DRAG_STEPS))
            largestWrongWayStep = maxOf(largestWrongWayStep, grid.position - previous)
            previous = grid.position
        }

        grid.position shouldBe 0
        largestWrongWayStep shouldBeLessThan EPISODE_HEIGHT
    }

    @Test
    fun `thumb position after a column count change follows the new layout`() {
        val lines = MeasuredGridLines()
        val threeColumns = FakeGrid(List(20) { 3 to 600 }, viewportHeight = 2200, maxSpan = 3)
        for (step in 0..DRAG_STEPS) threeColumns.dragTo(lines, step.toFloat() / DRAG_STEPS)

        val twoColumns = FakeGrid(List(30) { 2 to 600 }, viewportHeight = 2200, maxSpan = 2)
        twoColumns.scrollTo(GridScrollTarget(index = 30, scrollOffset = 0))
        lines.record(twoColumns.sample())

        val expected = twoColumns.position.toFloat() / twoColumns.maxScroll
        (abs(lines.scrollProportion() - expected) < 0.001f) shouldBe true
    }

    private fun assertDragDownIsEven(grid: FakeGrid) {
        val lines = MeasuredGridLines()
        val largestEvenStep = grid.maxScroll / 100
        var previous = 0
        for (step in 0..DRAG_STEPS) {
            grid.dragTo(lines, step.toFloat() / DRAG_STEPS)
            grid.position shouldBeGreaterThanOrEqual previous
            grid.position - previous shouldBeLessThan largestEvenStep
            previous = grid.position
        }
        grid.position shouldBe grid.maxScroll
    }

    private fun animeGrid() = FakeGrid(
        lines = listOf(1 to 816, 1 to 144, 1 to 156, 1 to 96) + List(40) { 1 to EPISODE_HEIGHT },
        viewportHeight = 2337,
        maxSpan = 3,
    )

    private fun libraryGrid() = FakeGrid(
        lines = listOf(1 to 150) + List(20) { 3 to 600 },
        viewportHeight = 2200,
        maxSpan = 3,
        lineSpacing = 10,
    )

    /** Lays out lines of (item count, height) the way LazyVerticalGrid places them for scrollToItem. */
    private class FakeGrid(
        private val lines: List<Pair<Int, Int>>,
        private val viewportHeight: Int,
        private val maxSpan: Int,
        private val lineSpacing: Int = 0,
    ) {
        private val firstIndexOfLine = lines.runningFold(0) { index, line -> index + line.first }
        private val totalItems = firstIndexOfLine.last()
        private val lineStarts = lines.runningFold(0) { start, line -> start + line.second + lineSpacing }
        val maxScroll = (lineStarts.last() - lineSpacing - viewportHeight).coerceAtLeast(0)
        var position = 0
            private set

        fun dragTo(lines: MeasuredGridLines, proportion: Float) {
            lines.record(sample())
            scrollTo(lines.scrollTargetAt(proportion))
        }

        fun scrollTo(target: GridScrollTarget) {
            val index = target.index.coerceIn(0, totalItems - 1)
            position = (lineStarts[lineOf(index)] + target.scrollOffset).coerceIn(0, maxScroll)
        }

        fun sample(): GridLayoutSample {
            val firstLine = lineStarts.indexOfLast { it <= position }.coerceAtMost(lines.lastIndex)
            val items = (firstLine..lines.lastIndex)
                .takeWhile { lineStarts[it] < position + viewportHeight }
                .flatMap { line ->
                    List(lines[line].first) { GridItemSample(firstIndexOfLine[line] + it, line, lines[line].second) }
                }
            return GridLayoutSample(
                totalItems = totalItems,
                maxSpan = maxSpan,
                items = items,
                firstVisibleItemIndex = firstIndexOfLine[firstLine],
                firstVisibleItemScrollOffset = position - lineStarts[firstLine],
                viewportHeight = viewportHeight,
                lineSpacing = lineSpacing,
            )
        }

        private fun lineOf(index: Int) = firstIndexOfLine.indexOfLast { it <= index }.coerceAtMost(lines.lastIndex)
    }

    private companion object {
        const val DRAG_STEPS = 2000
        const val EPISODE_HEIGHT = 400
    }
}
