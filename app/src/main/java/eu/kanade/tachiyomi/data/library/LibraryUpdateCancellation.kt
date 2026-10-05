package eu.kanade.tachiyomi.data.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun CoroutineScope.launchLibraryUpdateCancellation(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    action: suspend () -> Unit,
): Job = launch(dispatcher) {
    action()
}
