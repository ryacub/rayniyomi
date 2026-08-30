package eu.kanade.tachiyomi.ui.entries.manga.track

import eu.kanade.tachiyomi.data.track.Tracker
import tachiyomi.domain.track.manga.model.MangaTrack

sealed interface MangaTrackInfoItem {
    val tracker: Tracker

    data class Tracked(
        val track: MangaTrack,
        override val tracker: Tracker,
    ) : MangaTrackInfoItem

    data class Untracked(
        override val tracker: Tracker,
    ) : MangaTrackInfoItem
}
