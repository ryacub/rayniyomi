package eu.kanade.tachiyomi.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 * The focused example for `docs/coroutine-test-dispatchers.md`. It shows how a screen model that
 * owns its lifecycle scope takes a constructor-injected [CoroutineDispatcher], and why a launch
 * that escapes the injected dispatcher is invisible to the shared virtual-time scheduler.
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
     * A minimal fake screen model. It owns its lifecycle scope, takes a [CoroutineDispatcher]
     * constructor parameter with the production default, and launches exactly once on it.
     */
    private class FakeScreenModel(
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val started = CompletableDeferred<Unit>()

        init {
            scope.launch(dispatcher) {
                started.complete(Unit)
            }
        }

        fun cancel() {
            scope.cancel()
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
            model.cancel()
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
            model.cancel()
        }
}
