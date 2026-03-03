package eu.kanade.tachiyomi.ui.entries.common

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.enrichment.EntryEnrichmentCoordinator
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class EntryEnrichmentScreenModel(
    private val entryId: Long,
    private var title: String,
    private val mediaType: EnrichmentMediaType,
    private val coordinator: EntryEnrichmentCoordinator = Injekt.get(),
) : StateScreenModel<EntryEnrichmentScreenModel.State>(State()) {

    data class State(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val entry: EnrichedEntry? = null,
        val errorText: String? = null,
    )

    private val _announcements = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val announcements = _announcements.asSharedFlow()

    init {
        screenModelScope.launchIO {
            when (mediaType) {
                EnrichmentMediaType.MANGA -> coordinator.observeManga(entryId)
                EnrichmentMediaType.ANIME -> coordinator.observeAnime(entryId)
            }.collectLatest { cached ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        entry = cached,
                        errorText = cached?.failures?.takeIf { failures -> failures.isNotEmpty() }
                            ?.joinToString(", ") { failure -> "${failure.trackerName}: ${failure.userMessage}" },
                    )
                }
            }
        }

        refresh(manual = false)
    }

    fun updateTitle(title: String) {
        this.title = title
    }

    fun refresh(manual: Boolean) {
        if (state.value.refreshing) return
        screenModelScope.launchIO {
            if (manual) {
                _announcements.tryEmit("Syncing recommendations")
            }
            mutableState.update { it.copy(refreshing = true, errorText = null) }
            val result = runCatching {
                when (mediaType) {
                    EnrichmentMediaType.MANGA -> coordinator.refreshManga(entryId, title, force = true)
                    EnrichmentMediaType.ANIME -> coordinator.refreshAnime(entryId, title, force = true)
                }
            }
            result.onSuccess { entry ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        entry = entry,
                        errorText = entry.failures.takeIf { failures -> failures.isNotEmpty() }
                            ?.joinToString(", ") { failure -> "${failure.trackerName}: ${failure.userMessage}" },
                    )
                }
                if (manual) {
                    _announcements.tryEmit(
                        "${entry.recommendations.size} updated, ${entry.failures.size} failed",
                    )
                }
            }.onFailure {
                val message = it.message?.takeIf(String::isNotBlank) ?: "Unable to sync recommendations"
                mutableState.update { state ->
                    state.copy(
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
}
