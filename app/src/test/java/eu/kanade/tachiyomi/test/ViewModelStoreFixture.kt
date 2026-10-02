package eu.kanade.tachiyomi.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/** Builds [VM] inside this store through the public ViewModelProvider API, as Compose does. */
inline fun <reified VM : ViewModel> ViewModelStore.create(noinline factory: () -> VM): VM =
    ViewModelProvider.create(this, viewModelFactory { initializer { factory() } })[VM::class]

/** Builds a model in a fresh store, clears the store, and asserts that its viewModelScope is cancelled. */
inline fun <reified VM : ViewModel> assertClearingStoreCancelsScope(noinline factory: () -> VM) {
    val store = ViewModelStore()
    val job = store.create(factory).viewModelScope.coroutineContext.job

    assertFalse(job.isCancelled)
    store.clear()
    assertTrue(job.isCancelled)
}
