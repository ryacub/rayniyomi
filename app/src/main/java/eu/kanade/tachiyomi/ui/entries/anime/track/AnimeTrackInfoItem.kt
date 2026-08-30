package eu.kanade.tachiyomi.ui.entries.anime.track

import eu.kanade.tachiyomi.data.track.Tracker
import tachiyomi.domain.track.anime.model.AnimeTrack

sealed interface AnimeTrackInfoItem {
    val tracker: Tracker

    data class Tracked(
        val track: AnimeTrack,
        override val tracker: Tracker,
    ) : AnimeTrackInfoItem

    data class Untracked(
        override val tracker: Tracker,
    ) : AnimeTrackInfoItem
}
