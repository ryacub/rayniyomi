package eu.kanade.tachiyomi.ui.browse.manga.migration.search

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
import eu.kanade.tachiyomi.di.ViewModelSearchDispatcher
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.MangaSourceFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.manga.service.MangaSourceManager

@AssistedInject
class MigrateMangaSearchScreenModel(
    @Assisted val mangaId: Long,
    @Assisted initialExtensionFilter: String = "",
    sourcePreferences: SourcePreferences,
    sourceManager: MangaSourceManager,
    extensionManager: MangaExtensionManager,
    networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    preferences: SourcePreferences,
    @ViewModelSearchDispatcher searchDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5),
) : MangaSearchScreenModel(
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
            @Assisted mangaId: Long,
            @Assisted initialExtensionFilter: String,
        ): MigrateMangaSearchScreenModel
    }

    init {
        extensionFilter = initialExtensionFilter
        viewModelScope.launch {
            val manga = getManga.await(mangaId)!!
            mutableState.update {
                it.copy(
                    fromSourceId = manga.source,
                    searchQuery = manga.title,
                )
            }

            search()
        }
    }

    override fun getEnabledSources(): List<MangaSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != MangaSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { it.id != state.value.fromSourceId },
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }
}
