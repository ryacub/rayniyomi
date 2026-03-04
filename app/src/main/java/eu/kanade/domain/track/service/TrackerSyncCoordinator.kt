package eu.kanade.domain.track.service

import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.enrichment.BulkEnrichmentCoordinator
import eu.kanade.domain.track.enrichment.DiscoverFeedCoordinator
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class TrackerSyncCoordinator(
    private val trackPreferences: TrackPreferences,
    private val refreshAllMangaTracks: RefreshAllMangaTracks,
    private val refreshAllAnimeTracks: RefreshAllAnimeTracks,
    private val bulkEnrichmentCoordinator: BulkEnrichmentCoordinator,
    private val discoverFeedCoordinator: DiscoverFeedCoordinator,
) {

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
        runCatching { bulkEnrichmentCoordinator.refreshAll(force = false) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.WARN, error) { "Bulk tracker enrichment refresh failed" }
            }
        runCatching { discoverFeedCoordinator.refresh(limit = 40, force = false) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                logcat(LogPriority.WARN, error) { "Discover feed refresh failed" }
            }

        TrackerSyncResult(
            trigger = trigger,
            syncedItems = mangaResult.syncedCount + animeResult.syncedCount,
            failedItems = mangaResult.failures + animeResult.failures,
            unlinkedItems = mangaResult.unlinkedCount + animeResult.unlinkedCount,
        )
    }
}
