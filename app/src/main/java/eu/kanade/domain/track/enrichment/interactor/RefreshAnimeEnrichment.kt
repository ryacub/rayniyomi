package eu.kanade.domain.track.enrichment.interactor

import eu.kanade.domain.track.enrichment.EntryEnrichmentCoordinator
import eu.kanade.domain.track.enrichment.model.EnrichedEntry

class RefreshAnimeEnrichment(
    private val coordinator: EntryEnrichmentCoordinator,
) {
    suspend fun await(animeId: Long, title: String, force: Boolean): EnrichedEntry {
        return coordinator.refreshAnime(animeId, title, force)
    }
}
