package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.presentation.util.StateViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga

@AssistedInject
class MangaMigrateSearchScreenDialogScreenModel(
    @Assisted val mangaId: Long,
    private val getManga: GetManga,
) : StateViewModel<MangaMigrateSearchScreenDialogScreenModel.State>(State()) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted mangaId: Long): MangaMigrateSearchScreenDialogScreenModel
    }

    init {
        viewModelScope.launch {
            val manga = getManga.await(mangaId)!!

            mutableState.update {
                it.copy(manga = manga)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update {
            it.copy(dialog = dialog)
        }
    }

    @Immutable
    data class State(
        val manga: Manga? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Migrate(val manga: Manga) : Dialog
    }
}
