package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@AssistedInject
class ReaderSettingsScreenModel(
    @Assisted readerState: StateFlow<ReaderViewModel.State>,
    @Assisted val hasDisplayCutout: Boolean,
    @Assisted val onChangeReadingMode: (ReadingMode) -> Unit,
    @Assisted val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            readerState: StateFlow<ReaderViewModel.State>,
            hasDisplayCutout: Boolean,
            onChangeReadingMode: (ReadingMode) -> Unit,
            onChangeOrientation: (ReaderOrientation) -> Unit,
        ): ReaderSettingsScreenModel
    }

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, null)
}
