package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.StateViewModel
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

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
class AnimeExtensionFilterScreenModel(
    private val preferences: SourcePreferences,
    private val getExtensionLanguages: GetAnimeExtensionLanguages,
    private val toggleLanguage: ToggleLanguage,
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
