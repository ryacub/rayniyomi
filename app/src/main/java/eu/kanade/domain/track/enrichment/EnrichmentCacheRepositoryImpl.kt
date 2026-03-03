package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class EnrichmentCacheRepositoryImpl(
    private val mangaHandler: MangaDatabaseHandler,
    private val animeHandler: AnimeDatabaseHandler,
    private val json: Json,
) : EnrichmentCacheRepository {

    override suspend fun getManga(entryId: Long): EnrichedEntry? {
        val cached = mangaHandler.awaitOneOrNull {
            tracker_enrichment_cacheQueries.getByEntryId(entryId) { _, _, payloadJson, _, _, _, _ -> payloadJson }
        } ?: return null

        return json.decodeFromString<EnrichedEntry>(cached).withRecommendations(getMangaRecommendations(entryId))
    }

    override suspend fun getAnime(entryId: Long): EnrichedEntry? {
        val cached = animeHandler.awaitOneOrNull {
            tracker_enrichment_cacheQueries.getByEntryId(entryId) { _, _, payloadJson, _, _, _, _ -> payloadJson }
        } ?: return null

        return json.decodeFromString<EnrichedEntry>(cached).withRecommendations(getAnimeRecommendations(entryId))
    }

    override fun observeManga(entryId: Long): Flow<EnrichedEntry?> {
        return mangaHandler.subscribeToOneOrNull {
            tracker_enrichment_cacheQueries.getByEntryId(entryId) { _, _, payloadJson, _, _, _, _ -> payloadJson }
        }.map { payload ->
            payload?.let {
                json.decodeFromString<EnrichedEntry>(it).withRecommendations(getMangaRecommendations(entryId))
            }
        }
    }

    override fun observeAnime(entryId: Long): Flow<EnrichedEntry?> {
        return animeHandler.subscribeToOneOrNull {
            tracker_enrichment_cacheQueries.getByEntryId(entryId) { _, _, payloadJson, _, _, _, _ -> payloadJson }
        }.map { payload ->
            payload?.let {
                json.decodeFromString<EnrichedEntry>(it).withRecommendations(getAnimeRecommendations(entryId))
            }
        }
    }

    override suspend fun upsertManga(entryId: Long, entry: EnrichedEntry) {
        val encoded = json.encodeToString(entry.copy(recommendations = emptyList()))
        mangaHandler.await(inTransaction = true) {
            tracker_enrichment_cacheQueries.upsert(
                entryId = entryId,
                mediaType = entry.mediaType.name,
                payloadJson = encoded,
                updatedAt = entry.updatedAt,
                expiresAt = entry.expiresAt,
                sourceCount = entry.sourceCoverage.size.toLong(),
                errorSummary = entry.failures.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.trackerName },
            )
            tracker_enrichment_cacheQueries.deleteRecommendationsByEntryId(entryId)
            entry.recommendations.forEach { rec ->
                tracker_enrichment_cacheQueries.upsertRecommendation(
                    entryId = entryId,
                    mediaType = entry.mediaType.name,
                    canonicalKey = rec.stableKey,
                    title = rec.title,
                    targetUrl = rec.targetUrl,
                    trackerSource = rec.trackerSources.joinToString(", "),
                    rankScore = rec.rankScore,
                    inLibrary = rec.inLibrary,
                    confidence = rec.confidence,
                    sourceCount = rec.sourceCount.toLong(),
                    updatedAt = entry.updatedAt,
                )
            }
        }
    }

    override suspend fun upsertAnime(entryId: Long, entry: EnrichedEntry) {
        val encoded = json.encodeToString(entry.copy(recommendations = emptyList()))
        animeHandler.await(inTransaction = true) {
            tracker_enrichment_cacheQueries.upsert(
                entryId = entryId,
                mediaType = entry.mediaType.name,
                payloadJson = encoded,
                updatedAt = entry.updatedAt,
                expiresAt = entry.expiresAt,
                sourceCount = entry.sourceCoverage.size.toLong(),
                errorSummary = entry.failures.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.trackerName },
            )
            tracker_enrichment_cacheQueries.deleteRecommendationsByEntryId(entryId)
            entry.recommendations.forEach { rec ->
                tracker_enrichment_cacheQueries.upsertRecommendation(
                    entryId = entryId,
                    mediaType = entry.mediaType.name,
                    canonicalKey = rec.stableKey,
                    title = rec.title,
                    targetUrl = rec.targetUrl,
                    trackerSource = rec.trackerSources.joinToString(", "),
                    rankScore = rec.rankScore,
                    inLibrary = rec.inLibrary,
                    confidence = rec.confidence,
                    sourceCount = rec.sourceCount.toLong(),
                    updatedAt = entry.updatedAt,
                )
            }
        }
    }

    private suspend fun getMangaRecommendations(entryId: Long): List<AggregatedRecommendation> {
        return mangaHandler.awaitList {
            tracker_enrichment_cacheQueries.getRecommendationsByEntryId(entryId) {
                    _,
                    _,
                    canonicalKey,
                    title,
                    targetUrl,
                    trackerSource,
                    rankScore,
                    inLibrary,
                    confidence,
                    sourceCount,
                    _,
                ->
                AggregatedRecommendation(
                    stableKey = canonicalKey,
                    title = title,
                    targetUrl = targetUrl,
                    trackerSources = trackerSource.split(", ").filter { it.isNotBlank() },
                    sourceCount = sourceCount.toInt(),
                    confidence = confidence,
                    inLibrary = inLibrary,
                    rankScore = rankScore,
                )
            }
        }
    }

    private suspend fun getAnimeRecommendations(entryId: Long): List<AggregatedRecommendation> {
        return animeHandler.awaitList {
            tracker_enrichment_cacheQueries.getRecommendationsByEntryId(entryId) {
                    _,
                    _,
                    canonicalKey,
                    title,
                    targetUrl,
                    trackerSource,
                    rankScore,
                    inLibrary,
                    confidence,
                    sourceCount,
                    _,
                ->
                AggregatedRecommendation(
                    stableKey = canonicalKey,
                    title = title,
                    targetUrl = targetUrl,
                    trackerSources = trackerSource.split(", ").filter { it.isNotBlank() },
                    sourceCount = sourceCount.toInt(),
                    confidence = confidence,
                    inLibrary = inLibrary,
                    rankScore = rankScore,
                )
            }
        }
    }

    private fun EnrichedEntry.withRecommendations(recommendations: List<AggregatedRecommendation>): EnrichedEntry {
        return copy(recommendations = recommendations)
    }
}
