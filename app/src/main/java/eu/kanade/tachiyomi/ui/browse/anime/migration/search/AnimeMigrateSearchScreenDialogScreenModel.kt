package eu.kanade.tachiyomi.ui.browse.anime.migration.search

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
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime

@AssistedInject
class AnimeMigrateSearchScreenDialogScreenModel(
    @Assisted val animeId: Long,
    private val getAnime: GetAnime,
) : StateViewModel<AnimeMigrateSearchScreenDialogScreenModel.State>(State()) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted animeId: Long): AnimeMigrateSearchScreenDialogScreenModel
    }

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
