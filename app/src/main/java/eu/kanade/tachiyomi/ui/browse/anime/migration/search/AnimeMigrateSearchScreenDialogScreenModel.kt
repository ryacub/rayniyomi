package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime

class AnimeMigrateSearchScreenDialogScreenModel(
    val animeId: Long,
    getAnime: GetAnime = appGraph.getAnime,
) : StateViewModel<AnimeMigrateSearchScreenDialogScreenModel.State>(State()) {

    init {
        viewModelScope.launch {
            val anime = getAnime.await(animeId)!!

            mutableState.update {
                it.copy(anime = anime)
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
        val anime: Anime? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Migrate(val anime: Anime) : Dialog
    }
}
