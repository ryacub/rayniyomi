package eu.kanade.domain.track.service

import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.enrichment.BulkEnrichmentCoordinator
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class TrackerSyncCoordinator(
    private val trackPreferences: TrackPreferences,
    private val refreshAllMangaTracks: RefreshAllMangaTracks,
    private val refreshAllAnimeTracks: RefreshAllAnimeTracks,
    private val bulkEnrichmentCoordinator: BulkEnrichmentCoordinator,
) {
    companion object {
        private val BULK_ENRICHMENT_REFRESH_TIMEOUT: Duration = 30.seconds
    }

    suspend fun await(trigger: TrackerSyncTrigger): TrackerSyncResult = withIOContext {
        if (!trackPreferences.trackerSyncEnabled().get()) {
            return@withIOContext TrackerSyncResult(
                trigger = trigger,
                syncedItems = 0,
                failedItems = emptyList(),
                unlinkedItems = 0,
            )
        }

        val (mangaResult, animeResult) = supervisorScope {
            val manga = async { refreshAllMangaTracks.await() }
            val anime = async { refreshAllAnimeTracks.await() }
            manga.await() to anime.await()
        }

        trackPreferences.trackerSyncLastRunMillis().set(System.currentTimeMillis())
        runCatching {
            val completedInTime = withTimeoutOrNull(BULK_ENRICHMENT_REFRESH_TIMEOUT) {
                bulkEnrichmentCoordinator.refreshAll(force = false)
                true
            } ?: false
            if (!completedInTime) {
                logcat(LogPriority.WARN) {
                    "Bulk tracker enrichment refresh timed out after " +
                        "${BULK_ENRICHMENT_REFRESH_TIMEOUT.inWholeSeconds}s"
                }
            }
        }
            .onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.WARN, error) { "Bulk tracker enrichment refresh failed" }
            }

        TrackerSyncResult(
            trigger = trigger,
            syncedItems = mangaResult.syncedCount + animeResult.syncedCount,
            failedItems = mangaResult.failures + animeResult.failures,
            unlinkedItems = mangaResult.unlinkedCount + animeResult.unlinkedCount,
        )
    }
}
