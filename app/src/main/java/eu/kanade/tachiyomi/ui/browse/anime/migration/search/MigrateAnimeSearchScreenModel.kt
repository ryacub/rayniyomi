package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSearchScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.AnimeSourceFilter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime

class MigrateAnimeSearchScreenModel(
    val animeId: Long,
    initialExtensionFilter: String = "",
    getAnime: GetAnime = appGraph.getAnime,
) : AnimeSearchScreenModel() {

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
