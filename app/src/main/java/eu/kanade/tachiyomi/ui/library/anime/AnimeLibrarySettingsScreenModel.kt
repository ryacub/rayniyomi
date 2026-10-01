package eu.kanade.tachiyomi.ui.library.anime

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.time.Duration.Companion.seconds

class AnimeLibrarySettingsScreenModel(
    val preferences: BasePreferences = appGraph.basePreferences,
    val libraryPreferences: LibraryPreferences = appGraph.libraryPreferences,
    private val setAnimeDisplayMode: SetAnimeDisplayMode = appGraph.setAnimeDisplayMode,
    private val setSortModeForCategory: SetSortModeForAnimeCategory = appGraph.setSortModeForAnimeCategory,
    trackerManager: TrackerManager = appGraph.trackerManager,
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTrackedAnime(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setAnimeDisplayMode.await(mode)
    }

    fun setSort(
        category: Category?,
        mode: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }
}
