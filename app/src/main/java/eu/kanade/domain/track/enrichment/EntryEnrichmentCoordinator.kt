package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentFailure
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
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
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

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

    suspend fun refreshManga(mangaId: Long, title: String, force: Boolean): EnrichedEntry {
        return refreshEntry(
            entryId = mangaId,
            title = title,
            force = force,
            mediaType = EnrichmentMediaType.MANGA,
            getTracks = { getMangaTracks.await(mangaId) },
            getLibraryTitles = {
                getLibraryManga.await().map {
                    recommendationAggregator.normalizeTitleKey(it.manga.title)
                }.toSet()
            },
            getCached = suspend { cacheRepository.getManga(mangaId) },
            upsertCached = { cacheRepository.upsertManga(mangaId, it) },
            processTrack = { tracker, localTrack, title ->
                val mangaTrack = localTrack as MangaTrack
                val dbTrack = mangaTrack.toDbTrack()
                val remoteTrack = tracker.mangaService.refresh(dbTrack)
                val searchResults = tracker.mangaService.searchManga(title)
                val wrappedResults = searchResults.map { result ->
                    SearchResultWrapper(
                        remoteId = result.remote_id,
                        title = result.title,
                        trackingUrl = result.tracking_url,
                    )
                }
                ProcessedTrackInfo(
                    score = remoteTrack.score,
                    remoteId = mangaTrack.remoteId,
                    searchResults = wrappedResults,
                )
            },
            logSuffix = "mangaId=$mangaId",
        )
    }

    suspend fun refreshAnime(animeId: Long, title: String, force: Boolean): EnrichedEntry {
        return refreshEntry(
            entryId = animeId,
            title = title,
            force = force,
            mediaType = EnrichmentMediaType.ANIME,
            getTracks = { getAnimeTracks.await(animeId) },
            getLibraryTitles = {
                getLibraryAnime.await().map {
                    recommendationAggregator.normalizeTitleKey(it.anime.title)
                }.toSet()
            },
            getCached = suspend { cacheRepository.getAnime(animeId) },
            upsertCached = { cacheRepository.upsertAnime(animeId, it) },
            processTrack = { tracker, localTrack, title ->
                val animeTrack = localTrack as AnimeTrack
                val dbTrack = animeTrack.toDbTrack()
                val remoteTrack = tracker.animeService.refresh(dbTrack)
                val searchResults = tracker.animeService.searchAnime(title)
                val wrappedResults = searchResults.map { result ->
                    SearchResultWrapper(
                        remoteId = result.remote_id,
                        title = result.title,
                        trackingUrl = result.tracking_url,
                    )
                }
                ProcessedTrackInfo(
                    score = remoteTrack.score,
                    remoteId = animeTrack.remoteId,
                    searchResults = wrappedResults,
                )
            },
            logSuffix = "animeId=$animeId",
        )
    }

    fun observeManga(entryId: Long) = cacheRepository.observeManga(entryId)

    fun observeAnime(entryId: Long) = cacheRepository.observeAnime(entryId)

    private suspend fun <T : Any> refreshEntry(
        entryId: Long,
        title: String,
        force: Boolean,
        mediaType: EnrichmentMediaType,
        getTracks: suspend () -> List<T>,
        getLibraryTitles: suspend () -> Set<String>,
        getCached: suspend (Long) -> EnrichedEntry?,
        upsertCached: suspend (EnrichedEntry) -> Unit,
        processTrack: suspend (BaseTracker, T, String) -> ProcessedTrackInfo,
        logSuffix: String,
    ): EnrichedEntry = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = getCached(entryId)
        if (!force && cached != null && cached.expiresAt > now) return@withContext cached

        val tracks = getTracks()
        val failures = mutableListOf<EnrichmentFailure>()
        val candidates = mutableListOf<RecommendationAggregator.RecommendationCandidate>()
        val sourceCoverage = mutableListOf<String>()
        val scores = mutableListOf<Double>()

        val inLibraryTitles = getLibraryTitles()

        supervisorScope {
            tracks.mapNotNull { localTrack ->
                val trackerId = when (localTrack) {
                    is MangaTrack -> localTrack.trackerId
                    is AnimeTrack -> localTrack.trackerId
                    else -> return@mapNotNull null
                }
                val tracker = trackerManager.get(trackerId)?.takeIf { it.isLoggedIn }
                    ?: return@mapNotNull null

                async {
                    runCatching {
                        withTimeout(10_000) {
                            val processedInfo = processTrack(tracker, localTrack, title)
                            if (processedInfo.score > 0) scores += processedInfo.score
                            sourceCoverage += tracker.name

                            processedInfo.searchResults.take(4).forEach { rec ->
                                val search = rec as? SearchResultWrapper ?: return@forEach
                                if (search.remoteId == processedInfo.remoteId) return@forEach
                                val recommendationTitle = search.title.trim()
                                val canonical = canonicalKey(tracker.id, search.trackingUrl, search.remoteId).trim()
                                val fallbackNormalized = recommendationAggregator.normalizeTitleKey(recommendationTitle)
                                if (recommendationTitle.isBlank() || canonical.isBlank() ||
                                    fallbackNormalized.isBlank()
                                ) {
                                    logcat(LogPriority.WARN) {
                                        "Dropping invalid recommendation tracker=${tracker.name} remoteId=${search.remoteId}"
                                    }
                                    return@forEach
                                }
                                candidates += RecommendationAggregator.RecommendationCandidate(
                                    canonicalKey = canonical,
                                    fallbackKey = "fallback:$fallbackNormalized",
                                    title = recommendationTitle,
                                    targetUrl = search.trackingUrl.takeIf { it.isNotBlank() },
                                    trackerSource = tracker.name,
                                )
                            }
                        }
                    }.onFailure { error ->
                        val trackerId = when (localTrack) {
                            is MangaTrack -> localTrack.trackerId
                            is AnimeTrack -> localTrack.trackerId
                            else -> return@onFailure
                        }
                        val tracker = trackerManager.get(trackerId)
                        failures += EnrichmentFailure(
                            trackerId = trackerId,
                            trackerName = tracker?.name ?: "Unknown",
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
            entryId = entryId,
            mediaType = mediaType,
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

        upsertCached(result)
        logcat(LogPriority.INFO) {
            "Tracker enrichment $logSuffix coverage=${sourceCoverage.size} recs=${mergedRecommendations.size} failures=${failures.size}"
        }
        result
    }

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

    private data class ProcessedTrackInfo(
        val score: Double,
        val remoteId: Long,
        val searchResults: List<Any>,
    )

    private data class SearchResultWrapper(
        val remoteId: Long,
        val title: String,
        val trackingUrl: String,
    )
}
