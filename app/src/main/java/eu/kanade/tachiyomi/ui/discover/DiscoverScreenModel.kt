package eu.kanade.tachiyomi.ui.discover

import androidx.compose.material3.SnackbarHostState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.enrichment.DiscoverFeedCoordinator
import eu.kanade.domain.track.enrichment.model.DiscoverFeedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DiscoverScreenModel(
    private val coordinator: DiscoverFeedCoordinator = Injekt.get(),
) : StateScreenModel<DiscoverScreenModel.State>(State()) {

    data class State(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val items: List<DiscoverFeedItem> = emptyList(),
        val errorText: String? = null,
    )

    private val _announcements = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val announcements = _announcements.asSharedFlow()
    val snackbarHostState = SnackbarHostState()

    init {
        screenModelScope.launch {
            launch {
                coordinator.observe(limit = DISCOVER_LIMIT)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        val message = error.message?.takeIf { it.isNotBlank() } ?: "Unknown error"
                        mutableState.update {
                            it.copy(
                                loading = false,
                                errorText = message,
                            )
                        }
                    }
                    .collectLatest { items ->
                        mutableState.update {
                            it.copy(
                                loading = false,
                                items = items,
                            )
                        }
                    }
            }
            refresh(manual = false)
        }
    }

    fun refresh(manual: Boolean) {
        screenModelScope.launchIO {
            var startRefresh = false
            mutableState.update { current ->
                if (current.refreshing) return@update current
                startRefresh = true
                current.copy(
                    refreshing = true,
                    errorText = if (manual) null else current.errorText,
                )
            }
            if (!startRefresh) return@launchIO

            if (manual) {
                _announcements.tryEmit("Refreshing Discover feed")
            }

            runCatching {
                coordinator.refresh(limit = DISCOVER_LIMIT, force = manual)
            }.onSuccess { items ->
                mutableState.update {
                    it.copy(refreshing = false)
                }
                if (manual) {
                    _announcements.tryEmit("Discover updated: ${items.size} recommendations")
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Unable to refresh Discover"
                mutableState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        errorText = message,
                    )
                }
                if (manual) {
                    _announcements.tryEmit(message)
                }
            }
        }
    }

    private companion object {
        const val DISCOVER_LIMIT = 40
    }
}
