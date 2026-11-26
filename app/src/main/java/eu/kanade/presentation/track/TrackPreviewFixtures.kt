package eu.kanade.presentation.track

import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.ui.entries.track.EntryTrack
import eu.kanade.tachiyomi.ui.entries.track.EntryTrackInfoItem
import eu.kanade.tachiyomi.ui.entries.track.EntryTrackSearch
import eu.kanade.test.DummyTracker
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal fun previewTrackDateFormat(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

internal fun previewEntryTrackSearches(): Sequence<EntryTrackSearch> =
    generateSequence(0) { it + 1 }.map { index ->
        if (index % 2 == 0) {
            EntryTrackSearch.Manga(previewMangaTrackSearch(index))
        } else {
            EntryTrackSearch.Anime(previewAnimeTrackSearch(index))
        }
    }

internal fun previewAnimeTrackSearch(index: Int): AnimeTrackSearch {
    val fixture = previewTrackSearchFixture(index)
    return AnimeTrackSearch().apply {
        id = fixture.id
        anime_id = index.toLong()
        tracker_id = fixture.trackerId
        remote_id = fixture.remoteId
        library_id = fixture.libraryId
        title = fixture.title
        last_episode_seen = fixture.progress
        total_episodes = fixture.totalCount
        score = fixture.score
        status = fixture.status
        started_watching_date = 0L
        finished_watching_date = 0L
        tracking_url = fixture.trackingUrl
        cover_url = fixture.coverUrl
        start_date = fixture.startDate
        summary = fixture.summary
        publishing_status = fixture.publishingStatus
        publishing_type = if (fixture.hasPublishingType) "OVA" else ""
        artists = fixture.artists
        authors = fixture.authors
    }
}

internal fun previewMangaTrackSearch(index: Int): MangaTrackSearch {
    val fixture = previewTrackSearchFixture(index)
    return MangaTrackSearch().apply {
        id = fixture.id
        manga_id = index.toLong()
        tracker_id = fixture.trackerId
        remote_id = fixture.remoteId
        library_id = fixture.libraryId
        title = fixture.title
        last_chapter_read = fixture.progress
        total_chapters = fixture.totalCount
        score = fixture.score
        status = fixture.status
        started_reading_date = 0L
        finished_reading_date = 0L
        tracking_url = fixture.trackingUrl
        cover_url = fixture.coverUrl
        start_date = fixture.startDate
        summary = fixture.summary
        publishing_status = fixture.publishingStatus
        publishing_type = if (fixture.hasPublishingType) "Oneshot" else ""
        artists = fixture.artists
        authors = fixture.authors
    }
}

internal fun previewTrackItemWithoutTrack(): EntryTrackInfoItem =
    EntryTrackInfoItem.Untracked(
        tracker = previewDummyTracker(
            id = 1L,
            name = "Example Tracker",
        ),
    )

internal fun previewAnimeTrackItemWithTrack(privateTracking: Boolean = false): EntryTrackInfoItem =
    EntryTrackInfoItem.Tracked(
        track = EntryTrack.Anime(previewAnimeTrack(privateTracking = privateTracking)),
        tracker = previewDummyTracker(
            id = 2L,
            name = "Example Tracker 2",
        ),
    )

internal fun previewMangaTrackItemWithTrack(privateTracking: Boolean = false): EntryTrackInfoItem =
    EntryTrackInfoItem.Tracked(
        track = EntryTrack.Manga(previewMangaTrack(privateTracking = privateTracking)),
        tracker = previewDummyTracker(
            id = 2L,
            name = "Example Tracker 2",
        ),
    )

internal fun previewDummyTracker(
    id: Long,
    name: String,
): DummyTracker =
    DummyTracker(
        id = id,
        name = name,
    )

internal fun previewAnimeTrack(privateTracking: Boolean = false): AnimeTrack =
    AnimeTrack(
        id = 1L,
        animeId = 2L,
        trackerId = 3L,
        remoteId = 4L,
        libraryId = null,
        title = "Manage Name On Tracker Site",
        lastEpisodeSeen = 2.0,
        totalEpisodes = 12L,
        status = 1L,
        score = 2.0,
        remoteUrl = "https://example.com",
        startDate = 0L,
        finishDate = 0L,
        private = privateTracking,
    )

internal fun previewMangaTrack(privateTracking: Boolean = false): MangaTrack =
    MangaTrack(
        id = 1L,
        mangaId = 2L,
        trackerId = 3L,
        remoteId = 4L,
        libraryId = null,
        title = "Manage Name On Tracker Site",
        lastChapterRead = 2.0,
        totalChapters = 12L,
        status = 1L,
        score = 2.0,
        remoteUrl = "https://example.com",
        startDate = 0L,
        finishDate = 0L,
        private = privateTracking,
    )

private data class TrackSearchFixture(
    val id: Long,
    val trackerId: Long,
    val remoteId: Long,
    val libraryId: Long,
    val title: String,
    val progress: Double,
    val totalCount: Long,
    val score: Double,
    val status: Long,
    val trackingUrl: String,
    val coverUrl: String,
    val startDate: String,
    val summary: String,
    val publishingStatus: String,
    val hasPublishingType: Boolean,
    val artists: List<String>,
    val authors: List<String>,
)

private val previewSearchDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

private val previewSearchToday = LocalDate.of(2024, 1, 15)

private fun previewTrackSearchFixture(index: Int): TrackSearchFixture =
    TrackSearchFixture(
        id = index + 1L,
        trackerId = index % 3 + 1L,
        remoteId = index + 100L,
        libraryId = index + 1L,
        title = previewLorem(3 + index % 8).joinToString(),
        progress = (index * 7 % 101).toDouble(),
        totalCount = 100L + index * 37L % 900,
        score = (index % 11).toDouble(),
        status = index % 6 + 1L,
        trackingUrl = "https://example.com/tracker-example",
        coverUrl = "https://example.com/cover.png",
        startDate = previewSearchDateFormatter.format(previewSearchToday.minusDays(index + 1L)),
        summary = previewLorem(index % 41).joinToString(),
        publishingStatus = if (index % 2 == 0) "Finished" else "",
        hasPublishingType = index % 2 == 1,
        artists = previewNames(index),
        authors = previewNames(index + 1),
    )

private fun previewNames(index: Int): List<String> =
    (0..index % 4).map { previewLorem(3 + (index + it) % 3).joinToString() }

private fun previewLorem(words: Int): Sequence<String> =
    LoremIpsum(words).values
