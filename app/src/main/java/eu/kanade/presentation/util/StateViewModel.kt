package eu.kanade.presentation.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.plus

/**
 * A [ViewModel] that exposes one [StateFlow] of screen state.
 *
 * Create it only inside a Screen rendered by a Navigator. Outside one, `viewModel {}` binds to
 * the Activity store and the instance outlives the screen.
 */
abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    protected val mutableState: MutableStateFlow<S> = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()
}

/** [viewModelScope] on the IO dispatcher. Cleared with the ViewModel. */
val ViewModel.ioCoroutineScope: CoroutineScope
    get() = viewModelScope + Dispatchers.IO
