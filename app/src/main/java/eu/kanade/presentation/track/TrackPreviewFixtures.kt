package eu.kanade.presentation.track

import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import eu.kanade.tachiyomi.ui.entries.manga.track.MangaTrackItem
import eu.kanade.test.DummyTracker
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.random.Random

internal fun previewTrackDateFormat(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

internal fun previewAnimeTrackSearches(): Sequence<AnimeTrackSearch> = sequence {
    while (true) {
        yield(previewAnimeTrackSearch())
    }
}

internal fun previewMangaTrackSearches(): Sequence<MangaTrackSearch> = sequence {
    while (true) {
        yield(previewMangaTrackSearch())
    }
}

internal fun previewAnimeTrackSearch(): AnimeTrackSearch {
    val fixture = randomTrackSearchFixture()
    return AnimeTrackSearch().apply {
        id = fixture.id
        anime_id = Random.nextLong()
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

internal fun previewMangaTrackSearch(): MangaTrackSearch {
    val fixture = randomTrackSearchFixture()
    return MangaTrackSearch().apply {
        id = fixture.id
        manga_id = Random.nextLong()
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

internal fun previewAnimeTrackItemWithoutTrack(): AnimeTrackItem =
    AnimeTrackItem(
        track = null,
        tracker = previewDummyTracker(
            id = 1L,
            name = "Example Tracker",
        ),
    )

internal fun previewAnimeTrackItemWithTrack(privateTracking: Boolean = false): AnimeTrackItem =
    AnimeTrackItem(
        track = previewAnimeTrack(privateTracking = privateTracking),
        tracker = previewDummyTracker(
            id = 2L,
            name = "Example Tracker 2",
        ),
    )

internal fun previewMangaTrackItemWithoutTrack(): MangaTrackItem =
    MangaTrackItem(
        track = null,
        tracker = previewDummyTracker(
            id = 1L,
            name = "Example Tracker",
        ),
    )

internal fun previewMangaTrackItemWithTrack(privateTracking: Boolean = false): MangaTrackItem =
    MangaTrackItem(
        track = previewMangaTrack(privateTracking = privateTracking),
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

private val previewSearchDateFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private fun randomTrackSearchFixture(): TrackSearchFixture =
    TrackSearchFixture(
        id = Random.nextLong(),
        trackerId = Random.nextLong(),
        remoteId = Random.nextLong(),
        libraryId = Random.nextLong(),
        title = previewLorem((1..10).random()).joinToString(),
        progress = (0..100).random().toDouble(),
        totalCount = (100L..1000L).random(),
        score = (0..10).random().toDouble(),
        status = Random.nextLong(),
        trackingUrl = "https://example.com/tracker-example",
        coverUrl = "https://example.com/cover.png",
        startDate = previewSearchDateFormatter.format(Instant.now().minus((1L..365L).random(), ChronoUnit.DAYS)),
        summary = previewLorem((0..40).random()).joinToString(),
        publishingStatus = if (Random.nextBoolean()) "Finished" else "",
        hasPublishingType = Random.nextBoolean(),
        artists = randomNames(),
        authors = randomNames(),
    )

private fun randomNames(): List<String> =
    (0..(0..3).random()).map { previewLorem((3..5).random()).joinToString() }

private fun previewLorem(words: Int): Sequence<String> =
    LoremIpsum(words).values
