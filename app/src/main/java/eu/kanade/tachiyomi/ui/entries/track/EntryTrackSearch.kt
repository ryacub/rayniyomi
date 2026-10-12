package eu.kanade.tachiyomi.ui.entries.track

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch

sealed interface EntryTrackSearch {
    val title: String
    val coverUrl: String
    val trackingUrl: String
    val authors: List<String>
    val artists: List<String>
    val publishingType: String
    val publishingStatus: String
    val startDate: String
    val summary: String
    val score: Double
    var private: Boolean

    data class Manga(val search: MangaTrackSearch) : EntryTrackSearch {
        override val title get() = search.title
        override val coverUrl get() = search.cover_url
        override val trackingUrl get() = search.tracking_url
        override val authors get() = search.authors
        override val artists get() = search.artists
        override val publishingType get() = search.publishing_type
        override val publishingStatus get() = search.publishing_status
        override val startDate get() = search.start_date
        override val summary get() = search.summary
        override val score get() = search.score
        override var private
            get() = search.private
            set(value) {
                search.private = value
            }
    }

    data class Anime(val search: AnimeTrackSearch) : EntryTrackSearch {
        override val title get() = search.title
        override val coverUrl get() = search.cover_url
        override val trackingUrl get() = search.tracking_url
        override val authors get() = search.authors
        override val artists get() = search.artists
        override val publishingType get() = search.publishing_type
        override val publishingStatus get() = search.publishing_status
        override val startDate get() = search.start_date
        override val summary get() = search.summary
        override val score get() = search.score
        override var private
            get() = search.private
            set(value) {
                search.private = value
            }
    }
}
