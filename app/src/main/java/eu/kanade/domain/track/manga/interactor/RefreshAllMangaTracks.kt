package eu.kanade.domain.track.manga.interactor

import eu.kanade.domain.track.interactor.TrackSyncConflictResolver
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.domain.track.manga.model.toDomainTrack
import eu.kanade.domain.track.service.MediaType
import eu.kanade.domain.track.service.TrackerSyncFailure
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

class RefreshAllMangaTracks(
    private val getTracks: GetMangaTracks,
    private val trackerManager: TrackerManager,
    private val insertTrack: InsertMangaTrack,
    private val deleteTrack: DeleteMangaTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val updateChapter: UpdateChapter,
    private val conflictResolver: TrackSyncConflictResolver,
) {

    data class Result(
        val syncedCount: Int,
        val unlinkedCount: Int,
        val failures: List<TrackerSyncFailure>,
    )

    suspend fun await(): Result {
        var syncedCount = 0
        var unlinkedCount = 0
        val failures = mutableListOf<TrackerSyncFailure>()

        val allTracks = getTracks.awaitAll()

        allTracks.forEach { localTrack ->
            val tracker = trackerManager.get(localTrack.trackerId)
            if (tracker !is MangaTracker || !tracker.isLoggedIn) {
                return@forEach
            }

            try {
                val remoteTrack = tracker.refresh(localTrack.toDbTrack()).toDomainTrack() ?: return@forEach
                val chapters = getChaptersByMangaId.await(localTrack.mangaId)
                val resolution = conflictResolver.resolveManga(localTrack, remoteTrack, chapters)

                if (resolution.chapterUpdates.isNotEmpty()) {
                    updateChapter.awaitAll(resolution.chapterUpdates)
                }

                resolution.pushRemoteTrack?.let { pushTrack ->
                    tracker.update(pushTrack.toDbTrack())
                }

                insertTrack.await(resolution.mergedTrack)
                syncedCount++
            } catch (e: Throwable) {
                if (isDeletedRemoteEntry(e)) {
                    deleteTrack.await(localTrack.mangaId, localTrack.trackerId)
                    unlinkedCount++
                } else {
                    failures += TrackerSyncFailure(
                        tracker = tracker,
                        mediaType = MediaType.MANGA,
                        itemId = localTrack.mangaId,
                        message = e.message ?: "Unknown error",
                    )
                }
            }
        }

        return Result(
            syncedCount = syncedCount,
            unlinkedCount = unlinkedCount,
            failures = failures,
        )
    }

    private fun isDeletedRemoteEntry(error: Throwable): Boolean {
        val lowerMessage = error.message?.lowercase().orEmpty()
        return (error as? HttpException)?.code in setOf(404, 410) ||
            listOf("not found", "no match", "missing", "deleted", "does not exist").any { it in lowerMessage }
    }
}
