package eu.kanade.tachiyomi.ui.browse.manga.source

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.manga.model.Source
import java.util.SortedMap

class MangaSourcesFilterScreenModel(
    private val preferences: SourcePreferences = appGraph.sourcePreferences,
    private val getLanguagesWithSources: GetLanguagesWithMangaSources = appGraph.getLanguagesWithMangaSources,
    private val toggleSource: ToggleMangaSource = appGraph.toggleMangaSource,
    private val toggleLanguage: ToggleLanguage = appGraph.toggleLanguage,
) : StateScreenModel<MangaSourcesFilterScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launch {
            combine(
                getLanguagesWithSources.subscribe(),
                preferences.enabledLanguages().changes(),
                preferences.disabledMangaSources().changes(),
            ) { a, b, c -> Triple(a, b, c) }
                .catch { throwable ->
                    mutableState.update {
                        State.Error(
                            throwable = throwable,
                        )
                    }
                }
                .collectLatest { (languagesWithSources, enabledLanguages, disabledSources) ->
                    mutableState.update {
                        State.Success(
                            items = languagesWithSources,
                            enabledLanguages = enabledLanguages,
                            disabledSources = disabledSources,
                        )
                    }
                }
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Error(
            val throwable: Throwable,
        ) : State

        @Immutable
        data class Success(
            val items: SortedMap<String, List<Source>>,
            val enabledLanguages: Set<String>,
            val disabledSources: Set<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()
        }
    }
}
