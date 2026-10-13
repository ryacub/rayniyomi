package xyz.rayniyomi.cloudstream.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.rayniyomi.cloudstream.data.AvailablePlugins
import xyz.rayniyomi.cloudstream.data.PluginItem
import xyz.rayniyomi.cloudstream.data.PluginManager
import xyz.rayniyomi.cloudstream.data.PluginRows
import xyz.rayniyomi.cloudstream.data.PluginState
import xyz.rayniyomi.cloudstream.data.PluginStore
import xyz.rayniyomi.cloudstream.data.Repository
import xyz.rayniyomi.cloudstream.data.buildPluginRows

data class ManagerUiState(
    val consentGiven: Boolean = false,
    val repositories: List<Repository> = emptyList(),
    val rows: PluginRows = PluginRows(emptyList(), hiddenCount = 0),
    val repositoryErrors: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val busy: Set<String> = emptySet(),
    val message: String? = null,
)

class PluginManagerViewModel(
    private val manager: PluginManager,
    store: PluginStore,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val available = MutableStateFlow<List<AvailablePlugins>>(emptyList())
    private val activity = MutableStateFlow(Activity())

    val state: StateFlow<ManagerUiState> = combine(store.state, available, activity) { stored, lists, now ->
        stored.toUiState(lists, now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, store.state.value.toUiState(emptyList(), Activity()))

    init {
        if (store.state.value.repositories.isNotEmpty()) refresh()
    }

    fun giveConsent() = write { manager.giveConsent() }

    fun addRepository(url: String) {
        viewModelScope.launch {
            withLoading { manager.addRepository(url.trim()) }
                .onSuccess { reload() }
                .onFailure(::show)
        }
    }

    fun refresh() {
        viewModelScope.launch { withLoading { reload() } }
    }

    fun install(internalName: String) {
        viewModelScope.launch { installAll(listOf(internalName)) }
    }

    fun updateAll() {
        viewModelScope.launch {
            installAll(state.value.rows.items.filter(PluginItem::updateAvailable).map(PluginItem::internalName))
        }
    }

    fun setEnabled(internalName: String, enabled: Boolean) = write { manager.setEnabled(internalName, enabled) }

    fun remove(internalName: String) = write { manager.remove(internalName) }

    fun removeRepository(url: String) = write {
        manager.removeRepository(url)
        available.update { lists -> lists.filterNot { it.repository.url == url } }
    }

    fun dismissMessage() {
        activity.update { it.copy(message = null) }
    }

    private fun write(block: () -> Unit) {
        viewModelScope.launch(ioDispatcher) { block() }
    }

    private suspend fun installAll(names: List<String>) {
        val items = state.value.rows.items.filter { it.internalName in names && it.available != null }
        activity.update { it.copy(busy = it.busy + items.map(PluginItem::internalName)) }
        items.forEach { item ->
            withContext(ioDispatcher) { manager.install(checkNotNull(item.available), item.repositoryUrl) }
                .onFailure(::show)
            activity.update { it.copy(busy = it.busy - item.internalName) }
        }
    }

    private suspend fun reload() {
        available.value = withContext(ioDispatcher) { manager.availablePlugins() }
    }

    private suspend fun <T> withLoading(block: suspend () -> T): T {
        activity.update { it.copy(loading = true) }
        return try {
            block()
        } finally {
            activity.update { it.copy(loading = false) }
        }
    }

    private fun show(error: Throwable) {
        activity.update { it.copy(message = error.message ?: error.javaClass.simpleName) }
    }

    private data class Activity(
        val loading: Boolean = false,
        val busy: Set<String> = emptySet(),
        val message: String? = null,
    )

    private fun PluginState.toUiState(lists: List<AvailablePlugins>, now: Activity) = ManagerUiState(
        consentGiven = consentGiven,
        repositories = repositories,
        rows = buildPluginRows(this, lists.filter { list -> repositories.any { it.url == list.repository.url } }),
        repositoryErrors = lists.mapNotNull { list -> list.error?.let { list.repository.url to it } }.toMap(),
        loading = now.loading,
        busy = now.busy,
        message = now.message,
    )
}
