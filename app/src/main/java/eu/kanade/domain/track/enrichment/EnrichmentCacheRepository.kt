package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import kotlinx.coroutines.flow.Flow

interface EnrichmentCacheRepository {
    suspend fun getManga(entryId: Long): EnrichedEntry?
    suspend fun getAnime(entryId: Long): EnrichedEntry?
    fun observeManga(entryId: Long): Flow<EnrichedEntry?>
    fun observeAnime(entryId: Long): Flow<EnrichedEntry?>
    suspend fun upsertManga(entryId: Long, entry: EnrichedEntry)
    suspend fun upsertAnime(entryId: Long, entry: EnrichedEntry)
}
