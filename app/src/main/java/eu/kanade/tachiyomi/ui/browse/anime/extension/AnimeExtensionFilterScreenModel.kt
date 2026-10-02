package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.util.StateViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class AnimeExtensionFilterScreenModel(
    private val preferences: SourcePreferences = appGraph.sourcePreferences,
    private val getExtensionLanguages: GetAnimeExtensionLanguages = appGraph.getAnimeExtensionLanguages,
    private val toggleLanguage: ToggleLanguage = appGraph.toggleLanguage,
) : StateViewModel<AnimeExtensionFilterState>(AnimeExtensionFilterState.Loading) {

    private val _events: Channel<AnimeExtensionFilterEvent> = Channel()
    val events: Flow<AnimeExtensionFilterEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                getExtensionLanguages.subscribe(),
                preferences.enabledLanguages().changes(),
            ) { a, b -> a to b }
                .catch { throwable ->
                    logcat(LogPriority.ERROR, throwable)
                    _events.send(AnimeExtensionFilterEvent.FailedFetchingLanguages)
                }
                .collectLatest { (extensionLanguages, enabledLanguages) ->
                    mutableState.update {
                        AnimeExtensionFilterState.Success(
                            languages = extensionLanguages.toImmutableList(),
                            enabledLanguages = enabledLanguages.toImmutableSet(),
                        )
                    }
                }
        }
    }

    fun toggle(language: String) {
        toggleLanguage.await(language)
    }
}

sealed interface AnimeExtensionFilterEvent {
    data object FailedFetchingLanguages : AnimeExtensionFilterEvent
}

sealed interface AnimeExtensionFilterState {

    @Immutable
    data object Loading : AnimeExtensionFilterState

    @Immutable
    data class Success(
        val languages: ImmutableList<String>,
        val enabledLanguages: ImmutableSet<String> = persistentSetOf(),
    ) : AnimeExtensionFilterState {

        val isEmpty: Boolean
            get() = languages.isEmpty()
    }
}
