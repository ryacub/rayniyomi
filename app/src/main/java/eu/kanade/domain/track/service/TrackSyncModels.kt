package eu.kanade.domain.track.service

import eu.kanade.tachiyomi.data.track.Tracker

enum class TrackerSyncTrigger {
    MANUAL,
    FOREGROUND,
    PERIODIC,
}

data class TrackerSyncFailure(
    val tracker: Tracker,
    val mediaType: MediaType,
    val itemId: Long,
    val message: String,
)

enum class MediaType {
    MANGA,
    ANIME,
}

data class TrackerSyncResult(
    val trigger: TrackerSyncTrigger,
    val syncedItems: Int,
    val failedItems: List<TrackerSyncFailure>,
    val unlinkedItems: Int,
) {
    val failedCount: Int
        get() = failedItems.size
}
