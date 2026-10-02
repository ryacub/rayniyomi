package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.util.StateViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga

class MangaMigrateSearchScreenDialogScreenModel(
    val mangaId: Long,
    getManga: GetManga = appGraph.getManga,
) : StateViewModel<MangaMigrateSearchScreenDialogScreenModel.State>(State()) {

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
