package eu.kanade.tachiyomi.test

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * The focused example for `docs/coroutine-test-dispatchers.md`. It shows how a screen model
 * keeps its Voyager lifecycle scope, launches on a constructor-injected [CoroutineDispatcher],
 * and why a launch that escapes the injected dispatcher is invisible to the shared virtual-time
 * scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DispatcherInjectionExampleTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    /**
     * A minimal fake screen model. It extends Voyager [ScreenModel], keeps its lifecycle scope
     * (`screenModelScope`), takes a [CoroutineDispatcher] constructor parameter with the
     * production default, and launches exactly once on it.
     */
    private class FakeScreenModel(
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : ScreenModel {
        val started = CompletableDeferred<Unit>()

        init {
            screenModelScope.launch(dispatcher) {
                started.complete(Unit)
            }
        }
    }

    /**
     * A stand-in for any dispatcher the test scheduler does not own (for example the real
     * [Dispatchers.IO]). It records every dispatch request and never runs the work.
     */
    private class ForeignDispatcher : CoroutineDispatcher() {
        val dispatchCount = AtomicInteger(0)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount.incrementAndGet()
        }
    }

    @Test
    fun `a launch on the injected scheduler-owned dispatcher completes under advanceUntilIdle`() =
        runTest(vt.scheduler) {
            val model = FakeScreenModel(dispatcher = vt.io)

            advanceUntilIdle()

            assertTrue(model.started.isCompleted)
            model.started.await()
        }

    @Test
    fun `a launch that escapes to a foreign dispatcher is invisible to the shared scheduler`() =
        runTest(vt.scheduler) {
            val foreign = ForeignDispatcher()
            val model = FakeScreenModel(dispatcher = foreign)

            advanceUntilIdle()

            // The launch left the shared scheduler: the foreign dispatcher received exactly one
            // dispatch request. The scheduler cannot drain it, so the deferred never completes.
            assertEquals(1, foreign.dispatchCount.get())
            assertFalse(model.started.isCompleted)
        }
}
