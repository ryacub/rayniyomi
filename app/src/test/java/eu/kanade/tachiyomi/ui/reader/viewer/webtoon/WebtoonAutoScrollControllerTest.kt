package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebtoonAutoScrollControllerTest {

    @Test
    fun `start scrolls while recycler can scroll`() = runTest {
        var canScrollDown = true
        val deltas = mutableListOf<Int>()
        val stateChanges = mutableListOf<Boolean>()

        val controller = newController(
            canScrollDown = { canScrollDown },
            onScrollBy = { deltas.add(it) },
            onStateChanged = { stateChanges.add(it) },
            onReachedEnd = {},
        )

        controller.start()
        advanceTimeBy(100)

        assertTrue(controller.isRunning())
        assertTrue(deltas.sum() > 0)
        assertEquals(listOf(true), stateChanges)
    }

    @Test
    fun `pause stops further scrolling`() = runTest {
        val deltas = mutableListOf<Int>()

        val controller = newController(
            canScrollDown = { true },
            onScrollBy = { deltas.add(it) },
            onStateChanged = {},
            onReachedEnd = {},
        )

        controller.start()
        advanceTimeBy(50)
        val beforePause = deltas.sum()

        controller.pause()
        advanceTimeBy(100)

        assertFalse(controller.isRunning())
        assertEquals(beforePause, deltas.sum())
    }

    @Test
    fun `reaching end stops and calls end callback once`() = runTest {
        var canScrollDown = true
        var reachedEndCalls = 0
        val stateChanges = mutableListOf<Boolean>()

        val controller = newController(
            canScrollDown = { canScrollDown },
            onScrollBy = {},
            onStateChanged = { stateChanges.add(it) },
            onReachedEnd = { reachedEndCalls++ },
        )

        controller.start()
        advanceTimeBy(40)

        canScrollDown = false
        advanceTimeBy(40)

        assertFalse(controller.isRunning())
        assertEquals(1, reachedEndCalls)
        assertEquals(listOf(true, false), stateChanges)
    }

    @Test
    fun `higher speed produces more scrolling`() = runTest {
        val speedOneTotal = runWithSpeed(speedTenths = 10)
        val speedThreeTotal = runWithSpeed(speedTenths = 30)

        assertTrue(speedThreeTotal > speedOneTotal)
    }

    @Test
    fun `calling start repeatedly does not create duplicate loops`() = runTest {
        val singleStartTotal = runWithStartCalls(startCalls = 1)
        val repeatedStartTotal = runWithStartCalls(startCalls = 2)

        assertEquals(singleStartTotal, repeatedStartTotal)
    }

    @Test
    fun `toggle starts then pauses scrolling`() = runTest {
        val deltas = mutableListOf<Int>()
        val stateChanges = mutableListOf<Boolean>()
        val controller = newController(
            canScrollDown = { true },
            onScrollBy = { deltas.add(it) },
            onStateChanged = { stateChanges.add(it) },
            onReachedEnd = {},
        )

        controller.toggle()
        advanceTimeBy(50)
        val afterStartTotal = deltas.sum()

        controller.toggle()
        advanceTimeBy(50)

        assertTrue(afterStartTotal > 0)
        assertEquals(afterStartTotal, deltas.sum())
        assertFalse(controller.isRunning())
        assertEquals(listOf(true, false), stateChanges)
    }

    @Test
    fun `start at end only notifies onReachedEnd once until scrollable again`() = runTest {
        var canScrollDown = false
        var reachedEndCalls = 0
        val controller = newController(
            canScrollDown = { canScrollDown },
            onScrollBy = {},
            onStateChanged = {},
            onReachedEnd = { reachedEndCalls++ },
        )

        controller.start()
        controller.start()
        assertEquals(1, reachedEndCalls)

        canScrollDown = true
        controller.start()
        advanceTimeBy(20)
        canScrollDown = false
        advanceTimeBy(20)
        assertEquals(2, reachedEndCalls)
    }

    private fun TestScope.runWithSpeed(speedTenths: Int): Int {
        val deltas = mutableListOf<Int>()
        val controller = newController(
            canScrollDown = { true },
            onScrollBy = { deltas.add(it) },
            onStateChanged = {},
            onReachedEnd = {},
        )
        controller.setSpeedTenths(speedTenths)
        controller.start()
        advanceTimeBy(100)
        controller.pause()
        return deltas.sum()
    }

    private fun TestScope.runWithStartCalls(startCalls: Int): Int {
        val deltas = mutableListOf<Int>()
        val controller = newController(
            canScrollDown = { true },
            onScrollBy = { deltas.add(it) },
            onStateChanged = {},
            onReachedEnd = {},
        )
        controller.setSpeedTenths(10)
        repeat(startCalls) {
            controller.start()
        }
        advanceTimeBy(100)
        controller.pause()
        return deltas.sum()
    }

    private fun TestScope.newController(
        canScrollDown: () -> Boolean,
        onScrollBy: (Int) -> Unit,
        onStateChanged: (Boolean) -> Unit,
        onReachedEnd: () -> Unit,
    ): WebtoonAutoScrollController {
        return WebtoonAutoScrollController(
            scope = TestScope(StandardTestDispatcher(testScheduler)),
            canScrollDown = canScrollDown,
            onScrollBy = onScrollBy,
            onStateChanged = onStateChanged,
            onReachedEnd = onReachedEnd,
            basePxPerSecond = 100f,
            tickDelayMs = 10L,
        )
    }
}
