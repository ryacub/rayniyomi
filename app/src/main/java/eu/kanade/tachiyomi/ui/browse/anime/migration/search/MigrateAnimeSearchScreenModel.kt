package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@AssistedInject
class MigrateAnimeSearchScreenModel(
    @Assisted val animeId: Long,
    @Assisted initialExtensionFilter: String = "",
    sourcePreferences: SourcePreferences,
    sourceManager: AnimeSourceManager,
    extensionManager: AnimeExtensionManager,
    networkToLocalAnime: NetworkToLocalAnime,
    private val getAnime: GetAnime,
    preferences: SourcePreferences,
    @ViewModelSearchDispatcher searchDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5),
) : AnimeSearchScreenModel(
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
            @Assisted animeId: Long,
            @Assisted initialExtensionFilter: String,
        ): MigrateAnimeSearchScreenModel
    }

    init {
        extensionFilter = initialExtensionFilter
        viewModelScope.launch {
            val anime = getAnime.await(animeId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = anime.source,
                    searchQuery = anime.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
