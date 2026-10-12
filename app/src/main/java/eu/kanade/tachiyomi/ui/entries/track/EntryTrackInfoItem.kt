package eu.kanade.tachiyomi.ui.entries.track

import eu.kanade.tachiyomi.data.track.Tracker

sealed interface EntryTrackInfoItem {
    val tracker: Tracker

    data class Tracked(
        val track: EntryTrack,
        override val tracker: Tracker,
    ) : EntryTrackInfoItem

    data class Untracked(
        override val tracker: Tracker,
    ) : EntryTrackInfoItem
}
