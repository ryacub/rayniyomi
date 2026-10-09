package eu.kanade.domain.track.anime.interactor

import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.anime.model.toDomainTrack
import eu.kanade.domain.track.interactor.TrackSyncConflictResolver
import eu.kanade.domain.track.service.MediaType
import eu.kanade.domain.track.service.TrackerSyncFailure
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack

class RefreshAllAnimeTracks(
    private val getTracks: GetAnimeTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertAnimeTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val updateEpisode: UpdateEpisode,
    private val conflictResolver: TrackSyncConflictResolver,
) {

    data class Result(
        val syncedCount: Int,
        val failures: List<TrackerSyncFailure>,
    )

    suspend fun await(): Result {
        var syncedCount = 0
        val failures = mutableListOf<TrackerSyncFailure>()

        val allTracks = getTracks.awaitAll()

        allTracks.forEach { localTrack ->
            val tracker = trackerManager.get(localTrack.trackerId)
            if (tracker !is AnimeTracker || !tracker.isLoggedIn) {
                return@forEach
            }

            try {
                val remoteTrack = tracker.refresh(localTrack.toDbTrack()).toDomainTrack() ?: return@forEach
                val episodes = getEpisodesByAnimeId.await(localTrack.animeId)
                val resolution = conflictResolver.resolveAnime(localTrack, remoteTrack, episodes)

                if (resolution.episodeUpdates.isNotEmpty()) {
                    updateEpisode.awaitAll(resolution.episodeUpdates)
                }

                resolution.pushRemoteTrack?.let { pushTrack ->
                    tracker.update(pushTrack.toDbTrack())
                }

                insertTrack.await(resolution.mergedTrack)
                syncedCount++
            } catch (e: Throwable) {
                failures += TrackerSyncFailure(
                    tracker = tracker,
                    mediaType = MediaType.ANIME,
                    itemId = localTrack.animeId,
                    message = e.message ?: "Unknown error",
                )
            }
        }

        return Result(
            syncedCount = syncedCount,
            failures = failures,
        )
    }
}
