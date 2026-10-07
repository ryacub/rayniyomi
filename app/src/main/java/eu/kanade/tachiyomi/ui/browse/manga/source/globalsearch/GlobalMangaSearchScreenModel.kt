package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.di.ViewModelSearchDispatcher
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.manga.service.MangaSourceManager

@AssistedInject
class GlobalMangaSearchScreenModel(
    @Assisted initialQuery: String = "",
    @Assisted initialExtensionFilter: String? = null,
    sourcePreferences: SourcePreferences,
    sourceManager: MangaSourceManager,
    extensionManager: MangaExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    getManga: GetManga,
    preferences: SourcePreferences,
    @ViewModelSearchDispatcher searchDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5),
) : MangaSearchScreenModel(
    State(searchQuery = initialQuery),
    sourcePreferences = sourcePreferences,
    sourceManager = sourceManager,
    extensionManager = extensionManager,
    networkToLocalManga = networkToLocalManga,
    getManga = getManga,
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
        ): GlobalMangaSearchScreenModel
    }

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                // we're going to use custom extension filter instead
                setSourceFilter(MangaSourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<MangaSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != MangaSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
