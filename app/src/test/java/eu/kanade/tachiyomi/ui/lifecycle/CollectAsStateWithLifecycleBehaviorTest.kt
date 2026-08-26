package eu.kanade.tachiyomi.ui.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import eu.kanade.tachiyomi.test.VirtualTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

@OptIn(ExperimentalCoroutinesApi::class)
class CollectAsStateWithLifecycleBehaviorTest {

    private val vt = VirtualTime()

    private fun ownerIn(state: Lifecycle.State): TestLifecycleOwner = TestLifecycleOwner(
        initialState = state,
        coroutineDispatcher = Dispatchers.Main,
    )

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `lifecycle-gated collection pauses while stopped and resumes with latest state`() =
        runTest(vt.scheduler) {
            val owner = ownerIn(Lifecycle.State.CREATED)
            val upstream = MutableStateFlow(0)
            val observed = mutableListOf<Int>()

            val job = launch(Dispatchers.Main) {
                upstream
                    .flowWithLifecycle(owner.lifecycle, minActiveState = Lifecycle.State.STARTED)
                    .collect(observed::add)
            }
            advanceUntilIdle()

            // Collection stays gated while the lifecycle is below STARTED.
            assertEquals(emptyList<Int>(), observed)

            owner.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            assertEquals(listOf(0), observed)

            upstream.value = 1
            advanceUntilIdle()
            assertEquals(listOf(0, 1), observed)

            // The stop happens first, then the emission; the gated collector must not
            // observe the value while stopped.
            owner.currentState = Lifecycle.State.CREATED
            upstream.value = 2
            advanceUntilIdle()
            assertEquals(listOf(0, 1), observed)

            // Resume delivers the latest durable value exactly once.
            owner.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 2), observed)

            job.cancelAndJoin()
            advanceUntilIdle()
        }

    @Test
    fun `preference lifecycle bridge keeps initial value and catches latest durable state on resume`() =
        runTest(vt.scheduler) {
            val owner = ownerIn(Lifecycle.State.CREATED)
            val preference = FakePreference(initial = 41)
            val collectorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

            val bridgedState = preference.collectAsLifecycleState(owner, collectorScope)
            advanceUntilIdle()
            assertEquals(41, bridgedState.value)

            // Durable change below STARTED must not reach the bridged state.
            preference.set(42)
            advanceUntilIdle()
            assertEquals(41, bridgedState.value)

            owner.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            assertEquals(42, bridgedState.value)

            preference.set(43)
            advanceUntilIdle()
            assertEquals(43, bridgedState.value)

            collectorScope.coroutineContext.cancel()
            advanceUntilIdle()
        }

    @Test
    fun `ungated collection observes stopped-phase emission which proves the gate assertions detect faults`() =
        runTest(vt.scheduler) {
            // Fault injection: run the same scenario as the pause test, but collect
            // WITHOUT flowWithLifecycle. This collector MUST observe the emission made
            // while stopped, which proves the assertions above fail when the lifecycle
            // gate stops working instead of passing trivially.
            val owner = ownerIn(Lifecycle.State.CREATED)
            val upstream = MutableStateFlow(0)
            val observed = mutableListOf<Int>()

            val job = launch(Dispatchers.Main) {
                upstream.collect(observed::add)
            }
            advanceUntilIdle()

            owner.currentState = Lifecycle.State.STARTED
            advanceUntilIdle()
            upstream.value = 1
            advanceUntilIdle()
            assertEquals(listOf(0, 1), observed)

            owner.currentState = Lifecycle.State.CREATED
            upstream.value = 2
            advanceUntilIdle()
            assertEquals(listOf(0, 1, 2), observed)

            job.cancelAndJoin()
            advanceUntilIdle()
        }

    private class FakePreference(initial: Int) : Preference<Int> {
        private val state = MutableStateFlow(initial)

        override fun key(): String = "test_key"

        override fun get(): Int = state.value

        override fun set(value: Int) {
            state.value = value
        }

        override fun isSet(): Boolean = true

        override fun delete() = Unit
        override fun defaultValue(): Int = 0

        override fun changes(): Flow<Int> = state

        override fun stateIn(scope: CoroutineScope): StateFlow<Int> {
            return state.stateIn(scope, SharingStarted.Eagerly, state.value)
        }
    }

    private fun <T> Preference<T>.collectAsLifecycleState(
        lifecycleOwner: LifecycleOwner,
        scope: CoroutineScope,
    ): StateFlow<T> {
        val flow = changes().flowWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED,
        )
        return flow.stateIn(scope, SharingStarted.Eagerly, get())
    }
}
