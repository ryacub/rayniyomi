package eu.kanade.tachiyomi.ui.entries.common

import eu.kanade.tachiyomi.data.track.Tracker

internal data class EntryTrackingSummary(
    val trackingCount: Int,
    val hasLoggedInTrackers: Boolean,
)

internal object EntryTrackingSummaryObserver {
    fun <T> summarize(
        tracks: List<T>,
        loggedInTrackers: List<Tracker>,
        trackId: (T) -> Long,
        isTrackerSupported: (Tracker) -> Boolean,
    ): EntryTrackingSummary {
        val supportedTrackers = loggedInTrackers.filter(isTrackerSupported)
        val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
        val supportedTrackerTracks = tracks.filter { trackId(it) in supportedTrackerIds }
        return EntryTrackingSummary(
            trackingCount = supportedTrackerTracks.size,
            hasLoggedInTrackers = supportedTrackers.isNotEmpty(),
        )
    }
}
