package eu.kanade.presentation.util

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.test.create
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateViewModelTest {

    private class CounterViewModel : StateViewModel<Int>(0) {
        fun set(value: Int) {
            mutableState.value = value
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state exposes the value written through mutableState`() = runTest {
        val model = ViewModelStore().create { CounterViewModel() }

        model.state.value shouldBe 0
        model.set(5)
        model.state.value shouldBe 5
    }

    @Test
    fun `clearing the store cancels viewModelScope`() = runTest {
        val store = ViewModelStore()
        val model = store.create { CounterViewModel() }
        val job = model.viewModelScope.coroutineContext.job

        job.isCancelled shouldBe false
        store.clear()
        job.isCancelled shouldBe true
    }
}
