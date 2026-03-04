package eu.kanade.domain.track.enrichment.interactor

import eu.kanade.domain.track.enrichment.EntryEnrichmentCoordinator
import eu.kanade.domain.track.enrichment.model.EnrichedEntry

class RefreshMangaEnrichment(
    private val coordinator: EntryEnrichmentCoordinator,
) {
    suspend fun await(mangaId: Long, title: String, force: Boolean): EnrichedEntry {
        return coordinator.refreshManga(mangaId, title, force)
    }
}
