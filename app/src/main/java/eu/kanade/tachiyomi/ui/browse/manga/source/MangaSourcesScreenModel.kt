package eu.kanade.tachiyomi.ui.browse.manga.source

import android.app.Application
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.ToggleExcludeFromMangaDataSaver
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.presentation.browse.manga.MangaSourceUiModel
import eu.kanade.tachiyomi.util.system.LAST_USED_KEY
import eu.kanade.tachiyomi.util.system.PINNED_KEY
import eu.kanade.tachiyomi.util.system.activeNetworkState
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.manga.interactor.CheckSourceHealth
import tachiyomi.domain.source.manga.model.Pin
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.source.local.entries.manga.LocalMangaSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicInteger

class MangaSourcesScreenModel(
    private val preferences: BasePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val getEnabledSources: GetEnabledMangaSources = Injekt.get(),
    private val toggleSource: ToggleMangaSource = Injekt.get(),
    private val toggleSourcePin: ToggleMangaSourcePin = Injekt.get(),
    // SY -->
    private val toggleExcludeFromMangaDataSaver: ToggleExcludeFromMangaDataSaver = Injekt.get(),
    // SY <--
    private val checkSourceHealth: CheckSourceHealth = Injekt.get(),
    private val application: Application = Injekt.get(),
) : StateScreenModel<MangaSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getEnabledSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingSources)
                }
                .collectLatest(::collectLatestSources)
        }
        // SY -->
        sourcePreferences.dataSaver().changes()
            .onEach {
                mutableState.update {
                    it.copy(
                        dataSaverEnabled = sourcePreferences.dataSaver().get() != DataSaver.NONE,
                    )
                }
            }
            .launchIn(screenModelScope)
        // SY <--
        sourcePreferences.showBrokenMangaSources().changes()
            .onEach { show ->
                mutableState.update { it.copy(showBrokenSources = show) }
            }
            .launchIn(screenModelScope)
    }

    private fun collectLatestSources(sources: List<Source>) {
        mutableState.update { state ->
            val map = TreeMap<String, MutableList<Source>> { d1, d2 ->
                // Sources without a lang defined will be placed at the end
                when {
                    d1 == LAST_USED_KEY && d2 != LAST_USED_KEY -> -1
                    d2 == LAST_USED_KEY && d1 != LAST_USED_KEY -> 1
                    d1 == PINNED_KEY && d2 != PINNED_KEY -> -1
                    d2 == PINNED_KEY && d1 != PINNED_KEY -> 1
                    d1 == "" && d2 != "" -> 1
                    d2 == "" && d1 != "" -> -1
                    else -> d1.compareTo(d2)
                }
            }
            val byLang = sources.groupByTo(map) {
                when {
                    it.isUsedLast -> LAST_USED_KEY
                    Pin.Actual in it.pin -> PINNED_KEY
                    else -> it.lang
                }
            }

            state.copy(
                isLoading = false,
                items = byLang
                    .flatMap {
                        listOf(
                            MangaSourceUiModel.Header(it.key),
                            *it.value.map { source ->
                                MangaSourceUiModel.Item(source)
                            }.toTypedArray(),
                        )
                    }
                    .toImmutableList(),
            )
        }
    }

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    // SY -->
    fun toggleExcludeFromMangaDataSaver(source: Source) {
        toggleExcludeFromMangaDataSaver.await(source)
    }
    // SY <--

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun toggleShowBrokenSources() {
        val current = sourcePreferences.showBrokenMangaSources().get()
        sourcePreferences.showBrokenMangaSources().set(!current)
    }

    fun refreshHealthChecks() {
        val currentState = state.value
        if (currentState.isRefreshing) return

        val networkState = application.activeNetworkState()
        if (!networkState.isOnline) {
            screenModelScope.launch {
                _events.send(Event.NoNetwork)
            }
            return
        }

        mutableState.update { it.copy(isRefreshing = true) }

        screenModelScope.launchIO {
            val sourceIds = currentState.items
                .filterIsInstance<MangaSourceUiModel.Item>()
                .map { it.source }
                .filter { it.id != LocalMangaSource.ID && !it.isStub }
                .map { it.id }
                .distinct()

            val semaphore = Semaphore(3)
            val healthy = AtomicInteger(0)
            val degraded = AtomicInteger(0)
            val broken = AtomicInteger(0)

            val jobs = sourceIds.map { sourceId ->
                launch {
                    semaphore.withPermit {
                        try {
                            val result = checkSourceHealth.check(sourceId)
                            when (result.status) {
                                SourceHealthStatus.HEALTHY -> healthy.incrementAndGet()
                                SourceHealthStatus.DEGRADED -> degraded.incrementAndGet()
                                SourceHealthStatus.BROKEN -> broken.incrementAndGet()
                                SourceHealthStatus.UNKNOWN -> {}
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Health check failed for source $sourceId" }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }

            mutableState.update { it.copy(isRefreshing = false) }
            _events.send(Event.HealthCheckComplete(healthy.get(), degraded.get(), broken.get()))
        }
    }

    fun retryHealthCheck(source: Source) {
        screenModelScope.launchIO {
            try {
                checkSourceHealth.check(source.id)
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Retry health check failed for source ${source.id}" }
            }
        }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
        data object NoNetwork : Event
        data class HealthCheckComplete(val healthy: Int, val degraded: Int, val broken: Int) : Event
    }

    data class Dialog(val source: Source)

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<MangaSourceUiModel> = persistentListOf(),
        // SY -->
        val dataSaverEnabled: Boolean = false,
        // SY <--
        val isRefreshing: Boolean = false,
        val showBrokenSources: Boolean = false,
    ) {
        val isEmpty = items.isEmpty()
    }
}
