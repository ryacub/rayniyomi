package eu.kanade.tachiyomi.ui.entries.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.entries.manga.model.toDomainManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.data.source.manga.MangaSourceGateway
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class LinkMangaSourceScreen(
    private val mangaId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = assistedMetroViewModel<LinkMangaSourceScreenModel, LinkMangaSourceScreenModel.Factory> {
            create(mangaId)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var showSources by remember { mutableStateOf(false) }

        LaunchedEffect(state.linked) {
            if (state.linked) navigator.pop()
        }

        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(MR.strings.link_manga_source_find_another))
                val initiatingManga = state.initiatingManga
                if (initiatingManga != null) {
                    Text(text = initiatingManga.title)
                }

                val selectedSource = state.source
                OutlinedButton(
                    onClick = { showSources = true },
                    enabled = state.sources.isNotEmpty() && !state.linking,
                ) {
                    Text(
                        if (selectedSource == null) {
                            stringResource(MR.strings.link_manga_source_choose_source)
                        } else {
                            sourceLabel(selectedSource)
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = state.query,
                        onValueChange = screenModel::setQuery,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(MR.strings.link_manga_source_search_placeholder)) },
                        singleLine = true,
                        enabled = initiatingManga != null && !state.linking,
                    )
                    Button(
                        onClick = screenModel::search,
                        enabled =
                        selectedSource != null && state.query.isNotBlank() && !state.searching && !state.linking,
                    ) {
                        Text(stringResource(MR.strings.link_manga_source_search))
                    }
                }

                val error = state.error
                when {
                    state.searching || state.linking -> CircularProgressIndicator()
                    error != null -> Text(text = stringResource(error.resource))
                    initiatingManga == null -> CircularProgressIndicator()
                    state.sources.isEmpty() -> Text(stringResource(MR.strings.link_manga_source_no_enabled_sources))
                    state.searchCompleted && state.results.isEmpty() && selectedSource != null ->
                        Text(stringResource(MR.strings.link_manga_source_no_matches, selectedSource.name))
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.results, key = { "${it.source}:${it.url}" }) { candidate ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable {
                                screenModel.select(candidate)
                            }.padding(12.dp),
                        ) {
                            Text(candidate.title)
                            state.sources.firstOrNull { it.id == candidate.source }?.let { source ->
                                Text(sourceLabel(source))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        if (showSources) {
            AlertDialog(
                onDismissRequest = { showSources = false },
                title = { Text(stringResource(MR.strings.link_manga_source_choose_source)) },
                text = {
                    LazyColumn {
                        items(state.sources, key = { it.id }) { source ->
                            Text(
                                text = sourceLabel(source),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    screenModel.selectSource(source)
                                    showSources = false
                                }.padding(12.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSources = false }) { Text(stringResource(MR.strings.action_close)) }
                },
            )
        }

        state.candidate?.let { candidate ->
            val manga = state.initiatingManga
            val candidateSource = state.sources.firstOrNull { it.id == candidate.source }
            AlertDialog(
                onDismissRequest = screenModel::dismissCandidate,
                title = { Text(stringResource(MR.strings.link_manga_source_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(
                                MR.strings.link_manga_source_confirm_text,
                                manga?.title.orEmpty(),
                                state.primarySourceName,
                                candidate.title,
                                if (candidateSource != null) sourceLabel(candidateSource) else "",
                            ),
                        )
                        state.error?.let { Text(stringResource(it.resource)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = screenModel::dismissCandidate, enabled = !state.linking) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = screenModel::confirm, enabled = !state.linking) {
                        Text(stringResource(MR.strings.link_manga_source_confirm))
                    }
                },
            )
        }
    }
}

@Composable
private fun sourceLabel(source: MangaSource): String = stringResource(
    MR.strings.link_manga_source_name_language,
    source.name,
    LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current),
)

@AssistedInject
class LinkMangaSourceScreenModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val mangaSourceManager: MangaSourceManager,
    private val sourcePreferences: SourcePreferences,
    private val mangaSourceLinkRepository: MangaSourceLinkRepository,
) : StateViewModel<LinkMangaSourceScreenModel.State>(State()) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(@Assisted mangaId: Long): LinkMangaSourceScreenModel
    }

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val manga = getManga.await(mangaId)
            val sources = manga?.let { eligibleSources(it.source) }
                .orEmpty()
            mutableState.update {
                it.copy(
                    initiatingManga = manga,
                    primarySourceName = manga?.let { entry ->
                        mangaSourceManager.getOrStub(entry.source).name
                    }.orEmpty(),
                    sources = sources,
                    query = manga?.title.orEmpty(),
                    error = if (manga == null) LinkMangaSourceError.MissingManga else null,
                )
            }
            if (manga == null) return@launch
            combine(
                mangaSourceManager.sources,
                sourcePreferences.enabledLanguages().changes(),
                sourcePreferences.disabledMangaSources().changes(),
            ) { _, _, _ -> eligibleSources(manga.source) }.collect { eligible ->
                updateAvailableSources(eligible)
            }
        }
    }

    fun setQuery(query: String) {
        if (state.value.initiatingManga == null || state.value.linking) return
        updateSearchInput { it.copy(query = query) }
    }

    fun selectSource(source: MangaSource) {
        if (source !in state.value.sources || state.value.linking) return
        updateSearchInput { it.copy(source = source) }
    }

    private fun updateSearchInput(update: (State) -> State) {
        searchJob?.cancel()
        mutableState.update {
            update(
                it,
            ).copy(results = emptyList(), candidate = null, searching = false, searchCompleted = false, error = null)
        }
    }

    fun search() {
        val current = state.value
        val manga = current.initiatingManga ?: return
        val selectedSource = current.source ?: return
        val query = current.query.trim()
        if (query.isEmpty() || current.searching || current.linking) return
        val sources = eligibleSources(manga.source)
        val source = sources.firstOrNull { it.id == selectedSource.id }
        if (source == null) {
            updateAvailableSources(sources)
            return
        }

        mutableState.update {
            it.copy(searching = true, results = emptyList(), candidate = null, searchCompleted = false, error = null)
        }
        searchJob = viewModelScope.launch {
            try {
                val candidates = withContext(Dispatchers.IO) {
                    MangaSourceGateway.search(source, 1, query, MangaSourceGateway.filters(source))
                        .mangas.distinctBy { it.url }.map { it.toDomainManga(source.id) }
                }
                val sources = eligibleSources(manga.source)
                if (sources.none { it.id == source.id }) {
                    updateAvailableSources(sources)
                    return@launch
                }
                mutableState.update { it.copy(searching = false, results = candidates, searchCompleted = true) }
            } catch (e: TimeoutCancellationException) {
                ensureActive()
                mutableState.update { it.copy(searching = false, error = LinkMangaSourceError.SearchFailed) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                mutableState.update { it.copy(searching = false, error = LinkMangaSourceError.SearchFailed) }
            }
        }
    }

    fun select(candidate: Manga) {
        mutableState.update {
            if (it.linking || candidate !in it.results) it else it.copy(candidate = candidate, error = null)
        }
    }

    fun dismissCandidate() {
        if (!state.value.linking) mutableState.update { it.copy(candidate = null) }
    }

    fun confirm() {
        val current = state.value
        val candidate = current.candidate ?: return
        if (current.linking || current.linked) return
        mutableState.update { it.copy(linking = true, error = null) }
        viewModelScope.launch {
            try {
                val initiatingManga = getManga.await(mangaId)
                if (initiatingManga == null) {
                    mutableState.update { it.copy(linking = false, error = LinkMangaSourceError.MissingManga) }
                    return@launch
                }
                if (eligibleSources(initiatingManga.source).none { it.id == candidate.source }) {
                    mutableState.update { it.copy(linking = false, error = LinkMangaSourceError.SourceUnavailable) }
                    return@launch
                }

                when (mangaSourceLinkRepository.linkCandidate(initiatingManga.id, candidate)) {
                    LinkMangaSourcesResult.Linked -> mutableState.update { it.copy(linking = false, linked = true) }
                    LinkMangaSourcesResult.AlreadyLinked ->
                        mutableState.update { it.copy(linking = false, error = LinkMangaSourceError.AlreadyLinked) }
                    LinkMangaSourcesResult.Conflict ->
                        mutableState.update { it.copy(linking = false, error = LinkMangaSourceError.Conflict) }
                    LinkMangaSourcesResult.Invalid ->
                        mutableState.update { it.copy(linking = false, error = LinkMangaSourceError.Invalid) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(linking = false, error = LinkMangaSourceError.LinkFailed) }
            }
        }
    }

    private fun updateAvailableSources(sources: List<MangaSource>) {
        val selectedSource = state.value.source
        if (selectedSource != null && sources.none { it.id == selectedSource.id }) {
            searchJob?.cancel()
            mutableState.update {
                it.copy(
                    sources = sources,
                    source = null,
                    results = emptyList(),
                    candidate = if (it.linking) it.candidate else null,
                    searching = false,
                    searchCompleted = false,
                    error = LinkMangaSourceError.SourceUnavailable,
                )
            }
        } else {
            mutableState.update { it.copy(sources = sources) }
        }
    }

    private fun eligibleSources(originalSourceId: Long): List<MangaSource> {
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledMangaSources().get()
        return mangaSourceManager.getOnlineSources().filter {
            it.id != originalSourceId && it.lang in enabledLanguages && it.id.toString() !in disabledSources
        }.sortedBy { it.name.lowercase() }
    }

    data class State(
        val initiatingManga: Manga? = null,
        val primarySourceName: String = "",
        val sources: List<MangaSource> = emptyList(),
        val source: MangaSource? = null,
        val query: String = "",
        val results: List<Manga> = emptyList(),
        val candidate: Manga? = null,
        val searching: Boolean = false,
        val searchCompleted: Boolean = false,
        val linking: Boolean = false,
        val linked: Boolean = false,
        val error: LinkMangaSourceError? = null,
    )
}

enum class LinkMangaSourceError(val resource: StringResource) {
    MissingManga(MR.strings.link_manga_source_missing_manga),
    AlreadyLinked(MR.strings.link_manga_source_already_linked),
    SourceUnavailable(MR.strings.link_manga_source_unavailable),
    SameSource(MR.strings.link_manga_source_same_source),
    Conflict(MR.strings.link_manga_source_conflict),
    Invalid(MR.strings.link_manga_source_failed),
    SearchFailed(MR.strings.link_manga_source_search_failed),
    LinkFailed(MR.strings.link_manga_source_failed),
}
