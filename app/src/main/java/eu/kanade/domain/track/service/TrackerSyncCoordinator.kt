package eu.kanade.domain.track.service

import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import tachiyomi.core.common.util.lang.withIOContext

class TrackerSyncCoordinator(
    private val trackPreferences: TrackPreferences,
    private val refreshAllMangaTracks: RefreshAllMangaTracks,
    private val refreshAllAnimeTracks: RefreshAllAnimeTracks,
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

        val mangaResult = refreshAllMangaTracks.await()
        val animeResult = refreshAllAnimeTracks.await()

        trackPreferences.trackerSyncLastRunMillis().set(System.currentTimeMillis())

        TrackerSyncResult(
            trigger = trigger,
            syncedItems = mangaResult.syncedCount + animeResult.syncedCount,
            failedItems = mangaResult.failures + animeResult.failures,
            unlinkedItems = mangaResult.unlinkedCount + animeResult.unlinkedCount,
        )
    }
}
