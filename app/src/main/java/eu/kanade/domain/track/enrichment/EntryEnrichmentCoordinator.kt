package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentFailure
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

class EntryEnrichmentCoordinator(
    private val trackerManager: TrackerManager,
    private val getMangaTracks: GetMangaTracks,
    private val getAnimeTracks: GetAnimeTracks,
    private val getLibraryManga: GetLibraryManga,
    private val getLibraryAnime: GetLibraryAnime,
    private val cacheRepository: EnrichmentCacheRepository,
    private val recommendationAggregator: RecommendationAggregator,
    private val computeCompositeScore: eu.kanade.domain.track.enrichment.interactor.ComputeCompositeScore,
) {

    private val ttlSuccessMs = 24 * 60 * 60 * 1000L
    private val ttlPartialFailureMs = 2 * 60 * 60 * 1000L

    suspend fun refreshManga(mangaId: Long, title: String, force: Boolean): EnrichedEntry = withContext(
        Dispatchers.IO,
    ) {
        val now = System.currentTimeMillis()
        val cached = cacheRepository.getManga(mangaId)
        if (!force && cached != null && cached.expiresAt > now) return@withContext cached

        val tracks = getMangaTracks.await(mangaId)
        val failures = mutableListOf<EnrichmentFailure>()
        val candidates = mutableListOf<RecommendationAggregator.RecommendationCandidate>()
        val sourceCoverage = mutableListOf<String>()
        val scores = mutableListOf<Double>()

        val inLibraryTitles = getLibraryManga.await().map {
            recommendationAggregator.normalizeTitleKey(it.manga.title)
        }.toSet()

        supervisorScope {
            tracks.mapNotNull { localTrack ->
                val tracker =
                    trackerManager.get(localTrack.trackerId)?.takeIf { it.isLoggedIn } ?: return@mapNotNull null
                async {
                    runCatching {
                        withTimeout(10_000) {
                            val remoteTrack = tracker.mangaService.refresh(localTrack.toDbTrack())
                            val score = remoteTrack.score
                            if (score > 0) scores += score
                            sourceCoverage += tracker.name
                            val search = tracker.mangaService.searchManga(title).take(4)
                            search.forEach { rec ->
                                if (rec.remote_id == localTrack.remoteId) return@forEach
                                val recommendationTitle = rec.title.trim()
                                val canonical = canonicalKey(tracker.id, rec.tracking_url, rec.remote_id).trim()
                                val fallbackNormalized = recommendationAggregator.normalizeTitleKey(recommendationTitle)
                                if (recommendationTitle.isBlank() || canonical.isBlank() ||
                                    fallbackNormalized.isBlank()
                                ) {
                                    logcat(LogPriority.WARN) {
                                        "Dropping invalid manga recommendation tracker=${tracker.name} remoteId=${rec.remote_id}"
                                    }
                                    return@forEach
                                }
                                candidates += RecommendationAggregator.RecommendationCandidate(
                                    canonicalKey = canonical,
                                    fallbackKey = "fallback:$fallbackNormalized",
                                    title = recommendationTitle,
                                    targetUrl = rec.tracking_url.takeIf { it.isNotBlank() },
                                    trackerSource = tracker.name,
                                )
                            }
                        }
                    }.onFailure { error ->
                        failures += EnrichmentFailure(
                            trackerId = localTrack.trackerId,
                            trackerName = tracker.name,
                            category = "NETWORK",
                            userMessage = sanitizeError(error),
                            retriable = true,
                        )
                    }
                }
            }.forEach { it.await() }
        }

        val mergedRecommendations = recommendationAggregator.mergeAndRank(candidates) {
            inLibraryTitles.contains(recommendationAggregator.normalizeTitleKey(it))
        }
        val compositeScore = computeCompositeScore(scores)
        val expiresAt = now + if (failures.isEmpty()) ttlSuccessMs else ttlPartialFailureMs

        val result = EnrichedEntry(
            entryId = mangaId,
            mediaType = EnrichmentMediaType.MANGA,
            mergedTitle = title,
            compositeScore = compositeScore,
            confidenceLabel = confidenceLabel(sourceCoverage.size),
            sourceCoverage = sourceCoverage.distinct(),
            summary = when {
                failures.isNotEmpty() && mergedRecommendations.isNotEmpty() -> "Partial results from trackers"
                failures.isNotEmpty() -> "Unable to fetch tracker enrichment"
                else -> "Tracker enrichment updated"
            },
            recommendations = mergedRecommendations,
            failures = failures,
            updatedAt = now,
            expiresAt = expiresAt,
        )

        cacheRepository.upsertManga(mangaId, result)
        logcat(LogPriority.INFO) {
            "Tracker enrichment mangaId=$mangaId coverage=${sourceCoverage.size} recs=${mergedRecommendations.size} failures=${failures.size}"
        }
        result
    }

    suspend fun refreshAnime(animeId: Long, title: String, force: Boolean): EnrichedEntry = withContext(
        Dispatchers.IO,
    ) {
        val now = System.currentTimeMillis()
        val cached = cacheRepository.getAnime(animeId)
        if (!force && cached != null && cached.expiresAt > now) return@withContext cached

        val tracks = getAnimeTracks.await(animeId)
        val failures = mutableListOf<EnrichmentFailure>()
        val candidates = mutableListOf<RecommendationAggregator.RecommendationCandidate>()
        val sourceCoverage = mutableListOf<String>()
        val scores = mutableListOf<Double>()

        val inLibraryTitles = getLibraryAnime.await().map {
            recommendationAggregator.normalizeTitleKey(it.anime.title)
        }.toSet()

        supervisorScope {
            tracks.mapNotNull { localTrack ->
                val tracker =
                    trackerManager.get(localTrack.trackerId)?.takeIf { it.isLoggedIn } ?: return@mapNotNull null
                async {
                    runCatching {
                        withTimeout(10_000) {
                            val remoteTrack = tracker.animeService.refresh(localTrack.toDbTrack())
                            val score = remoteTrack.score
                            if (score > 0) scores += score
                            sourceCoverage += tracker.name
                            val search = tracker.animeService.searchAnime(title).take(4)
                            search.forEach { rec ->
                                if (rec.remote_id == localTrack.remoteId) return@forEach
                                val recommendationTitle = rec.title.trim()
                                val canonical = canonicalKey(tracker.id, rec.tracking_url, rec.remote_id).trim()
                                val fallbackNormalized = recommendationAggregator.normalizeTitleKey(recommendationTitle)
                                if (recommendationTitle.isBlank() || canonical.isBlank() ||
                                    fallbackNormalized.isBlank()
                                ) {
                                    logcat(LogPriority.WARN) {
                                        "Dropping invalid anime recommendation tracker=${tracker.name} remoteId=${rec.remote_id}"
                                    }
                                    return@forEach
                                }
                                candidates += RecommendationAggregator.RecommendationCandidate(
                                    canonicalKey = canonical,
                                    fallbackKey = "fallback:$fallbackNormalized",
                                    title = recommendationTitle,
                                    targetUrl = rec.tracking_url.takeIf { it.isNotBlank() },
                                    trackerSource = tracker.name,
                                )
                            }
                        }
                    }.onFailure { error ->
                        failures += EnrichmentFailure(
                            trackerId = localTrack.trackerId,
                            trackerName = tracker.name,
                            category = "NETWORK",
                            userMessage = sanitizeError(error),
                            retriable = true,
                        )
                    }
                }
            }.forEach { it.await() }
        }

        val mergedRecommendations = recommendationAggregator.mergeAndRank(candidates) {
            inLibraryTitles.contains(recommendationAggregator.normalizeTitleKey(it))
        }
        val compositeScore = computeCompositeScore(scores)
        val expiresAt = now + if (failures.isEmpty()) ttlSuccessMs else ttlPartialFailureMs

        val result = EnrichedEntry(
            entryId = animeId,
            mediaType = EnrichmentMediaType.ANIME,
            mergedTitle = title,
            compositeScore = compositeScore,
            confidenceLabel = confidenceLabel(sourceCoverage.size),
            sourceCoverage = sourceCoverage.distinct(),
            summary = when {
                failures.isNotEmpty() && mergedRecommendations.isNotEmpty() -> "Partial results from trackers"
                failures.isNotEmpty() -> "Unable to fetch tracker enrichment"
                else -> "Tracker enrichment updated"
            },
            recommendations = mergedRecommendations,
            failures = failures,
            updatedAt = now,
            expiresAt = expiresAt,
        )

        cacheRepository.upsertAnime(animeId, result)
        logcat(LogPriority.INFO) {
            "Tracker enrichment animeId=$animeId coverage=${sourceCoverage.size} recs=${mergedRecommendations.size} failures=${failures.size}"
        }
        result
    }

    fun observeManga(entryId: Long) = cacheRepository.observeManga(entryId)

    fun observeAnime(entryId: Long) = cacheRepository.observeAnime(entryId)

    private fun canonicalKey(trackerId: Long, url: String, remoteId: Long): String {
        return url.takeIf { it.isNotBlank() }?.trim()?.lowercase() ?: "tracker:$trackerId:$remoteId"
    }

    private fun sanitizeError(error: Throwable): String {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Request failed"
        return if (message.length > 120) message.take(120) else message
    }

    private fun confidenceLabel(sourceCount: Int): String {
        return when {
            sourceCount >= 3 -> "high"
            sourceCount == 2 -> "medium"
            sourceCount == 1 -> "low"
            else -> "none"
        }
    }
}
