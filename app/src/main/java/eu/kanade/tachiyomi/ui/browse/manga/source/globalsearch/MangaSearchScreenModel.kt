package eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.di.ViewModelSearchDispatcher
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.browse.common.search.SearchRequestCoordinator
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.toggle
import tachiyomi.data.source.manga.MangaSourceGateway
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager

abstract class MangaSearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences,
    private val sourceManager: MangaSourceManager,
    private val extensionManager: MangaExtensionManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val getManga: GetManga,
    private val preferences: SourcePreferences,
    @ViewModelSearchDispatcher private val searchDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5),
) : StateViewModel<MangaSearchScreenModel.State>(initialState) {

    private val requestCoordinator = SearchRequestCoordinator()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledMangaSources().get()
    protected val pinnedSources = sourcePreferences.pinnedMangaSources().get()

    private var lastQuery: String? = null
    private var lastSourceFilter: MangaSourceFilter? = null

    protected var extensionFilter: String? = null

    private val sortComparator = { map: Map<MangaSource, MangaSearchItemResult> ->
        compareBy<MangaSource>(
            { (map[it] as? MangaSearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        viewModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<MangaSource> {
        return sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun getSelectedSources(): List<MangaSource> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        return extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filterIsInstance<MangaSource>()
            .filter { it in enabledSources }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: MangaSourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return
        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()
        val requestId = requestCoordinator.nextRequestId()
        val sources = getSelectedSources()

        updateItems { current ->
            sources
                .associateWith { source ->
                    if (sameQuery) current[source] ?: MangaSearchItemResult.Loading else MangaSearchItemResult.Loading
                }
                .toPersistentMap()
        }
        searchJob = viewModelScope.launch(searchDispatcher) {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is MangaSearchItemResult.Loading) {
                        return@async
                    }
                    searchSource(source, query, requestId)
                }
            }
                .awaitAll()
        }
    }

    fun retrySource(source: MangaSource) {
        val query = lastQuery ?: return
        if (state.value.items[source] !is MangaSearchItemResult.Error) return

        val requestId = requestCoordinator.latestRequestId()
        updateItem(source, MangaSearchItemResult.Loading)
        viewModelScope.launch(searchDispatcher) {
            searchSource(source, query, requestId)
        }
    }

    private suspend fun searchSource(source: MangaSource, query: String, requestId: Long) {
        val result = try {
            val page = withContext(searchDispatcher) {
                MangaSourceGateway.search(source, 1, query, MangaSourceGateway.filters(source))
            }
            MangaSearchItemResult.Success(page.mangas.map { networkToLocalManga.await(it.toDomainManga(source.id)) })
        } catch (e: LinkageError) {
            // A defective extension fails to link against the app's shared libraries.
            MangaSearchItemResult.Error(e)
        } catch (e: Exception) {
            MangaSearchItemResult.Error(e)
        }

        if (currentCoroutineContext().isActive && requestCoordinator.isLatest(requestId)) {
            updateItem(source, result)
        }
    }

    private fun updateItems(
        transform: (
            PersistentMap<MangaSource, MangaSearchItemResult>,
        ) -> PersistentMap<MangaSource, MangaSearchItemResult>,
    ) {
        mutableState.update { state ->
            val items = transform(state.items)
            state.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: MangaSource, result: MangaSearchItemResult) {
        updateItems { it.put(source, result) }
    }

    @Immutable
    data class State(
        val fromSourceId: Long? = null,
        val searchQuery: String? = null,
        val sourceFilter: MangaSourceFilter = MangaSourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<MangaSource, MangaSearchItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is MangaSearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }
}

enum class MangaSourceFilter {
    All,
    PinnedOnly,
}

sealed interface MangaSearchItemResult {
    data object Loading : MangaSearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : MangaSearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : MangaSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
