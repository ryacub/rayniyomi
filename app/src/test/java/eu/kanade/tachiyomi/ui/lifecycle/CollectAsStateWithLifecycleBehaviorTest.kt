package eu.kanade.tachiyomi.ui.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class CollectAsStateWithLifecycleBehaviorTest {

    private lateinit var mainDispatcher: CloseableCoroutineDispatcher

    @BeforeEach
    fun setUp() {
        mainDispatcher = newSingleThreadContext("LifecycleTestMain")
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        runBlocking(mainDispatcher) {
            // Drain pending lifecycle/cancellation callbacks before resetting Main.
            delay(10)
        }
        Dispatchers.resetMain()
        mainDispatcher.close()
    }

    @Test
    fun `lifecycle-gated collection pauses while stopped and resumes with latest state`() = runBlocking {
        val owner = TestLifecycleOwner(
            initialState = Lifecycle.State.CREATED,
            coroutineDispatcher = Dispatchers.Main,
        )
        val upstream = MutableStateFlow(0)
        val observed = mutableListOf<Int>()

        val job = launch(Dispatchers.Main) {
            upstream
                .flowWithLifecycle(owner.lifecycle, minActiveState = Lifecycle.State.STARTED)
                .collect(observed::add)
        }

        delay(20)
        assertEquals(emptyList<Int>(), observed)

        owner.currentState = Lifecycle.State.STARTED
        delay(20)
        assertEquals(listOf(0), observed)

        upstream.value = 1
        delay(20)
        assertEquals(listOf(0, 1), observed)

        owner.currentState = Lifecycle.State.CREATED
        upstream.value = 2
        delay(20)
        assertEquals(listOf(0, 1), observed)

        owner.currentState = Lifecycle.State.STARTED
        delay(20)
        assertEquals(listOf(0, 1, 2), observed)

        job.cancelAndJoin()
    }

    @Test
    fun `preference lifecycle bridge keeps initial value and catches latest durable state on resume`() = runBlocking {
        val owner = TestLifecycleOwner(
            initialState = Lifecycle.State.CREATED,
            coroutineDispatcher = Dispatchers.Main,
        )
        val preference = FakePreference(initial = 41)
        val collectorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        val bridgedState = preference.collectAsLifecycleState(owner, collectorScope)
        delay(20)
        assertEquals(41, bridgedState.value)

        preference.set(42)
        delay(20)
        assertEquals(41, bridgedState.value)

        owner.currentState = Lifecycle.State.STARTED
        delay(20)
        assertEquals(42, bridgedState.value)

        preference.set(43)
        delay(20)
        assertEquals(43, bridgedState.value)

        collectorScope.coroutineContext.cancel()
        delay(20)
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
