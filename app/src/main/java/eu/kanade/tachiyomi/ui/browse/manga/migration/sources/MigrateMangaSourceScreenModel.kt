package eu.kanade.tachiyomi.ui.browse.manga.migration.sources

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.model.Source

class MigrateMangaSourceScreenModel(
    preferences: SourcePreferences = appGraph.sourcePreferences,
    private val getSourcesWithFavoriteCount: GetMangaSourcesWithFavoriteCount =
        appGraph.getMangaSourcesWithFavoriteCount,
    private val setMigrateSorting: SetMigrateSorting = appGraph.setMigrateSorting,
) : StateViewModel<MigrateMangaSourceScreenModel.State>(State()) {

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            getSourcesWithFavoriteCount.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = sources.toImmutableList(),
                        )
                    }
                }
        }

        preferences.migrationSortingDirection().changes()
            .onEach { mutableState.update { state -> state.copy(sortingDirection = it) } }
            .launchIn(viewModelScope)

        preferences.migrationSortingMode().changes()
            .onEach { mutableState.update { state -> state.copy(sortingMode = it) } }
            .launchIn(viewModelScope)
    }

    fun toggleSortingMode() {
        with(state.value) {
            val newMode = when (sortingMode) {
                SetMigrateSorting.Mode.ALPHABETICAL -> SetMigrateSorting.Mode.TOTAL
                SetMigrateSorting.Mode.TOTAL -> SetMigrateSorting.Mode.ALPHABETICAL
            }

            setMigrateSorting.await(newMode, sortingDirection)
        }
    }

    fun toggleSortingDirection() {
        with(state.value) {
            val newDirection = when (sortingDirection) {
                SetMigrateSorting.Direction.ASCENDING -> SetMigrateSorting.Direction.DESCENDING
                SetMigrateSorting.Direction.DESCENDING -> SetMigrateSorting.Direction.ASCENDING
            }

            setMigrateSorting.await(sortingMode, newDirection)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<Pair<Source, Long>> = persistentListOf(),
        val sortingMode: SetMigrateSorting.Mode = SetMigrateSorting.Mode.ALPHABETICAL,
        val sortingDirection: SetMigrateSorting.Direction = SetMigrateSorting.Direction.ASCENDING,
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Event {
        data object FailedFetchingSourcesWithCount : Event
    }
}
