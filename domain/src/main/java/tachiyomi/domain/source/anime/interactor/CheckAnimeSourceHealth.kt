package tachiyomi.domain.source.anime.interactor

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository
import kotlin.coroutines.cancellation.CancellationException

class CheckAnimeSourceHealth(
    private val sourceManager: AnimeSourceManager,
    private val healthRepository: SourceHealthRepository,
) {

    suspend fun check(sourceId: Long): SourceHealth {
        val source = sourceManager.get(sourceId)

        // Source not available — skip, leave as UNKNOWN
        if (source == null) {
            return SourceHealth(
                sourceId = sourceId,
                status = SourceHealthStatus.UNKNOWN,
                lastCheckedAt = System.currentTimeMillis(),
                failureCount = 0,
                lastError = null,
            )
        }

        // Not a catalogue source (e.g. stub) — skip
        val catalogueSource = source as? AnimeCatalogueSource ?: return SourceHealth(
            sourceId = sourceId,
            status = SourceHealthStatus.UNKNOWN,
            lastCheckedAt = System.currentTimeMillis(),
            failureCount = 0,
            lastError = null,
        )

        val existing = healthRepository.get(sourceId)
        val now = System.currentTimeMillis()

        return try {
            withTimeout(HEALTH_CHECK_TIMEOUT_MS) {
                catalogueSource.getPopularAnime(1)
            }
            // Any successful response (even empty) means the source is reachable
            val health = SourceHealth(
                sourceId = sourceId,
                status = SourceHealthStatus.HEALTHY,
                lastCheckedAt = now,
                failureCount = 0,
                lastError = null,
            )
            healthRepository.upsert(health)
            health
        } catch (e: TimeoutCancellationException) {
            onFailure(sourceId, existing, now, "Check timed out (10s)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(sourceId, existing, now, e.message ?: e::class.simpleName ?: "Unknown error")
        }
    }

    private suspend fun onFailure(
        sourceId: Long,
        existing: SourceHealth?,
        now: Long,
        error: String,
    ): SourceHealth {
        val newFailureCount = (existing?.failureCount ?: 0) + 1
        val status = if (newFailureCount >= BROKEN_THRESHOLD) {
            SourceHealthStatus.BROKEN
        } else {
            SourceHealthStatus.DEGRADED
        }
        val health = SourceHealth(
            sourceId = sourceId,
            status = status,
            lastCheckedAt = now,
            failureCount = newFailureCount,
            lastError = error,
        )
        healthRepository.upsert(health)
        return health
    }

    companion object {
        private const val HEALTH_CHECK_TIMEOUT_MS = 10_000L
        private const val BROKEN_THRESHOLD = 3
    }
}
