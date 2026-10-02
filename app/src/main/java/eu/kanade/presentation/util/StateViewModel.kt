package eu.kanade.presentation.util

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A [ViewModel] that exposes one [StateFlow] of screen state, like Voyager's StateScreenModel.
 *
 * Create it only inside a Screen rendered by a Navigator. Outside one, `viewModel {}` binds to
 * the Activity store and the instance outlives the screen.
 */
abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    protected val mutableState: MutableStateFlow<S> = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()
}
