package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ReaderSettingsScreenModel(
    readerState: StateFlow<ReaderViewModel.State>,
    val hasDisplayCutout: Boolean,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences = appGraph.readerPreferences,
) : ViewModel() {

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)
}
