package eu.kanade.tachiyomi.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Builds [VM] inside this store through the public ViewModelProvider API, as Compose does. */
inline fun <reified VM : ViewModel> ViewModelStore.create(noinline factory: () -> VM): VM =
    ViewModelProvider.create(this, viewModelFactory { initializer { factory() } })[VM::class]
