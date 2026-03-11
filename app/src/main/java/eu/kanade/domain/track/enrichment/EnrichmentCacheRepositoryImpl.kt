package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.DiscoverCacheSnapshot
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
        }.flatMapLatest { payload ->
            flow {
                emit(
                    payload?.let {
                        json.decodeFromString<EnrichedEntry>(it).withRecommendations(getMangaRecommendations(entryId))
                    },
                )
            }
        }
    }

    override fun observeAnime(entryId: Long): Flow<EnrichedEntry?> {
        return animeHandler.subscribeToOneOrNull {
            tracker_enrichment_cacheQueries.getByEntryId(entryId) { _, _, payloadJson, _, _, _, _ -> payloadJson }
        }.flatMapLatest { payload ->
            flow {
                emit(
                    payload?.let {
                        json.decodeFromString<EnrichedEntry>(it).withRecommendations(getAnimeRecommendations(entryId))
                    },
                )
            }
        }
    }

    override suspend fun upsertManga(entryId: Long, entry: EnrichedEntry) {
        val encoded = json.encodeToString(entry.copy(recommendations = emptyList()))
        mangaHandler.await(inTransaction = true) {
            tracker_enrichment_cacheQueries.deleteRecommendationsByEntryId(entryId)
            tracker_enrichment_cacheQueries.upsert(
                entryId = entryId,
                mediaType = entry.mediaType.name,
                payloadJson = encoded,
                updatedAt = entry.updatedAt,
                expiresAt = entry.expiresAt,
                sourceCount = entry.sourceCoverage.size.toLong(),
                errorSummary = entry.failures.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.trackerName },
            )
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
            tracker_enrichment_cacheQueries.deleteRecommendationsByEntryId(entryId)
            tracker_enrichment_cacheQueries.upsert(
                entryId = entryId,
                mediaType = entry.mediaType.name,
                payloadJson = encoded,
                updatedAt = entry.updatedAt,
                expiresAt = entry.expiresAt,
                sourceCount = entry.sourceCoverage.size.toLong(),
                errorSummary = entry.failures.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.trackerName },
            )
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

    override suspend fun getDiscoverRecommendations(limit: Long): List<DiscoverRecommendationRecord> {
        val mangaRecommendations = mangaHandler.awaitList {
            tracker_enrichment_cacheQueries.getAllRecommendations(limit) {
                    entryId,
                    mediaType,
                    canonicalKey,
                    title,
                    targetUrl,
                    trackerSource,
                    rankScore,
                    inLibrary,
                    confidence,
                    sourceCount,
                    updatedAt,
                ->
                DiscoverRecommendationRecord(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    recommendation = AggregatedRecommendation(
                        stableKey = canonicalKey,
                        title = title,
                        targetUrl = targetUrl,
                        trackerSources = trackerSource.split(", ").filter { it.isNotBlank() },
                        sourceCount = sourceCount.toInt(),
                        confidence = confidence,
                        inLibrary = inLibrary,
                        rankScore = rankScore,
                    ),
                    updatedAt = updatedAt,
                )
            }
        }
        val animeRecommendations = animeHandler.awaitList {
            tracker_enrichment_cacheQueries.getAllRecommendations(limit) {
                    entryId,
                    mediaType,
                    canonicalKey,
                    title,
                    targetUrl,
                    trackerSource,
                    rankScore,
                    inLibrary,
                    confidence,
                    sourceCount,
                    updatedAt,
                ->
                DiscoverRecommendationRecord(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    recommendation = AggregatedRecommendation(
                        stableKey = canonicalKey,
                        title = title,
                        targetUrl = targetUrl,
                        trackerSources = trackerSource.split(", ").filter { it.isNotBlank() },
                        sourceCount = sourceCount.toInt(),
                        confidence = confidence,
                        inLibrary = inLibrary,
                        rankScore = rankScore,
                    ),
                    updatedAt = updatedAt,
                )
            }
        }
        return (mangaRecommendations + animeRecommendations)
            .sortedByDescending { it.recommendation.rankScore }
            .take(limit.toInt())
    }

    override suspend fun getDiscoverSnapshots(): List<DiscoverCacheSnapshot> {
        val mangaSnapshots = mangaHandler.awaitList {
            tracker_enrichment_cacheQueries.getAllCaches {
                    entryId,
                    mediaType,
                    payloadJson,
                    updatedAt,
                    expiresAt,
                    _,
                    _,
                ->
                DiscoverCacheSnapshot(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    updatedAt = updatedAt,
                    expiresAt = expiresAt,
                    compositeScore = runCatching {
                        json.decodeFromString<EnrichedEntry>(payloadJson).compositeScore
                    }.getOrNull(),
                )
            }
        }
        val animeSnapshots = animeHandler.awaitList {
            tracker_enrichment_cacheQueries.getAllCaches {
                    entryId,
                    mediaType,
                    payloadJson,
                    updatedAt,
                    expiresAt,
                    _,
                    _,
                ->
                DiscoverCacheSnapshot(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    updatedAt = updatedAt,
                    expiresAt = expiresAt,
                    compositeScore = runCatching {
                        json.decodeFromString<EnrichedEntry>(payloadJson).compositeScore
                    }.getOrNull(),
                )
            }
        }
        return mangaSnapshots + animeSnapshots
    }

    override fun observeDiscoverRecommendations(limit: Long): Flow<List<DiscoverRecommendationRecord>> {
        val mangaFlow = mangaHandler.subscribeToList {
            tracker_enrichment_cacheQueries.getAllRecommendations(limit) {
                    entryId,
                    mediaType,
                    canonicalKey,
                    title,
                    targetUrl,
                    trackerSource,
                    rankScore,
                    inLibrary,
                    confidence,
                    sourceCount,
                    updatedAt,
                ->
                DiscoverRecommendationRecord(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    recommendation = AggregatedRecommendation(
                        stableKey = canonicalKey,
                        title = title,
                        targetUrl = targetUrl,
                        trackerSources = trackerSource.split(", ").filter { it.isNotBlank() },
                        sourceCount = sourceCount.toInt(),
                        confidence = confidence,
                        inLibrary = inLibrary,
                        rankScore = rankScore,
                    ),
                    updatedAt = updatedAt,
                )
            }
        }
        val animeFlow = animeHandler.subscribeToList {
            tracker_enrichment_cacheQueries.getAllRecommendations(limit) {
                    entryId,
                    mediaType,
                    canonicalKey,
                    title,
                    targetUrl,
                    trackerSource,
                    rankScore,
                    inLibrary,
                    confidence,
                    sourceCount,
                    updatedAt,
                ->
                DiscoverRecommendationRecord(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    recommendation = AggregatedRecommendation(
                        stableKey = canonicalKey,
                        title = title,
                        targetUrl = targetUrl,
                        trackerSources = trackerSource.split(", ").filter { it.isNotBlank() },
                        sourceCount = sourceCount.toInt(),
                        confidence = confidence,
                        inLibrary = inLibrary,
                        rankScore = rankScore,
                    ),
                    updatedAt = updatedAt,
                )
            }
        }
        return combine(mangaFlow, animeFlow) { manga, anime ->
            (manga + anime)
                .sortedByDescending { it.recommendation.rankScore }
                .take(limit.toInt())
        }
    }

    override fun observeDiscoverSnapshots(): Flow<List<DiscoverCacheSnapshot>> {
        val mangaFlow = mangaHandler.subscribeToList {
            tracker_enrichment_cacheQueries.getAllCaches {
                    entryId,
                    mediaType,
                    payloadJson,
                    updatedAt,
                    expiresAt,
                    _,
                    _,
                ->
                DiscoverCacheSnapshot(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    updatedAt = updatedAt,
                    expiresAt = expiresAt,
                    compositeScore = runCatching {
                        json.decodeFromString<EnrichedEntry>(payloadJson).compositeScore
                    }.getOrNull(),
                )
            }
        }
        val animeFlow = animeHandler.subscribeToList {
            tracker_enrichment_cacheQueries.getAllCaches {
                    entryId,
                    mediaType,
                    payloadJson,
                    updatedAt,
                    expiresAt,
                    _,
                    _,
                ->
                DiscoverCacheSnapshot(
                    entryId = entryId,
                    mediaType = mediaType.toEnrichmentMediaType(),
                    updatedAt = updatedAt,
                    expiresAt = expiresAt,
                    compositeScore = runCatching {
                        json.decodeFromString<EnrichedEntry>(payloadJson).compositeScore
                    }.getOrNull(),
                )
            }
        }
        return combine(mangaFlow, animeFlow) { manga, anime -> manga + anime }
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

    private fun String.toEnrichmentMediaType(): EnrichmentMediaType {
        return runCatching { EnrichmentMediaType.valueOf(this) }.getOrDefault(EnrichmentMediaType.MANGA)
    }

    internal fun encodeTrackerSources(sources: List<String>): String {
        return json.encodeToString(sources)
    }

    internal fun decodeTrackerSources(encoded: String): List<String> {
        return json.decodeFromString<List<String>>(encoded).filter { it.isNotBlank() }
    }
}
