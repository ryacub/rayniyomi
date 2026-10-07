package eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.di.ViewModelSearchDispatcher
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@AssistedInject
class GlobalAnimeSearchScreenModel(
    @Assisted initialQuery: String = "",
    @Assisted initialExtensionFilter: String? = null,
    sourcePreferences: SourcePreferences,
    sourceManager: AnimeSourceManager,
    extensionManager: AnimeExtensionManager,
    networkToLocalAnime: NetworkToLocalAnime,
    getAnime: GetAnime,
    preferences: SourcePreferences,
    @ViewModelSearchDispatcher searchDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5),
) : AnimeSearchScreenModel(
    State(searchQuery = initialQuery),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalAnime = networkToLocalAnime,
    getAnime = getAnime,
    preferences = preferences,
    searchDispatcher = searchDispatcher,
) {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(
            @Assisted initialQuery: String,
            @Assisted initialExtensionFilter: String?,
        ): GlobalAnimeSearchScreenModel
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(AnimeSourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
