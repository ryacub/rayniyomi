package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.DiscoverCacheSnapshot
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import kotlinx.coroutines.flow.Flow

interface EnrichmentCacheRepository {
    suspend fun getManga(entryId: Long): EnrichedEntry?
    suspend fun getAnime(entryId: Long): EnrichedEntry?
    fun observeManga(entryId: Long): Flow<EnrichedEntry?>
    fun observeAnime(entryId: Long): Flow<EnrichedEntry?>
    suspend fun upsertManga(entryId: Long, entry: EnrichedEntry)
    suspend fun upsertAnime(entryId: Long, entry: EnrichedEntry)
    suspend fun getDiscoverRecommendations(limit: Long): List<DiscoverRecommendationRecord>
    suspend fun getDiscoverSnapshots(): List<DiscoverCacheSnapshot>
    fun observeDiscoverRecommendations(limit: Long): Flow<List<DiscoverRecommendationRecord>>
    fun observeDiscoverSnapshots(): Flow<List<DiscoverCacheSnapshot>>
}

data class DiscoverRecommendationRecord(
    val entryId: Long,
    val mediaType: EnrichmentMediaType,
    val recommendation: AggregatedRecommendation,
    val updatedAt: Long,
)
