package eu.kanade.tachiyomi.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualTime {
    val scheduler = TestCoroutineScheduler()
    val main = UnconfinedTestDispatcher(scheduler)
    val io = StandardTestDispatcher(scheduler)

    fun setUpMain() {
        Dispatchers.setMain(main)
    }

    fun tearDownMain() {
        Dispatchers.resetMain()
    }
}

/**
 * Drains the shared scheduler until idle (twice, so chained scheduling rounds settle), bounded
 * by a 5 s virtual-time timeout that catches infinite delay loops, then asserts [condition];
 * fails with a dump of [state] if false. Never read model state before calling this.
 */
suspend fun <T> TestScope.awaitAssert(state: () -> T, condition: (T) -> Boolean) {
    val value = withTimeout(5_000) {
        advanceUntilIdle()
        advanceUntilIdle()
        state()
    }
    if (!condition(value)) throw AssertionError("Condition not met after advancing until idle; state=$value")
}
