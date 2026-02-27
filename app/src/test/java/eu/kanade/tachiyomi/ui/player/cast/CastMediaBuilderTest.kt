package eu.kanade.tachiyomi.ui.player.cast

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class CastMediaBuilderTest {

    private lateinit var builder: CastMediaBuilder

    private val testAnime = Anime.create().copy(title = "Test Anime")
    private val testEpisode = Episode.create().copy(name = "Episode 1")

    @BeforeEach
    fun setup() {
        builder = CastMediaBuilder()
    }

    private fun createVideo(
        videoUrl: String,
        subtitles: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ): Video = Video(
        videoUrl = videoUrl,
        subtitleTracks = subtitles,
        audioTracks = audioTracks,
    )

    // ---- Content type detection ----

    @Test
    fun `build sets contentType to application x-mpegURL for m3u8 URLs`() {
        val video = createVideo("https://example.com/stream.m3u8")
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        assertEquals("application/x-mpegURL", mediaInfo.contentType)
    }

    @Test
    fun `build sets contentType to application dash+xml for mpd URLs`() {
        val video = createVideo("https://example.com/stream.mpd")
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        assertEquals("application/dash+xml", mediaInfo.contentType)
    }

    @Test
    fun `build sets contentType to video mp4 for generic https URLs`() {
        val video = createVideo("https://example.com/video.mp4")
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        assertEquals("video/mp4", mediaInfo.contentType)
    }

    // ---- Subtitle track handling ----

    @Test
    fun `build includes srt subtitle tracks as MediaTrack objects`() {
        val subtitles = listOf(Track("https://example.com/sub.srt", "English"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        val tracks = mediaInfo.mediaTracks ?: emptyList()
        assertEquals(1, tracks.size)
        assertEquals("https://example.com/sub.srt", tracks[0].contentId)
    }

    @Test
    fun `build includes vtt subtitle tracks as MediaTrack objects`() {
        val subtitles = listOf(Track("https://example.com/sub.vtt", "French"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        val tracks = mediaInfo.mediaTracks ?: emptyList()
        assertEquals(1, tracks.size)
        assertEquals("https://example.com/sub.vtt", tracks[0].contentId)
    }

    @Test
    fun `build skips ass subtitle tracks`() {
        val subtitles = listOf(Track("https://example.com/sub.ass", "Japanese"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        val tracks = mediaInfo.mediaTracks ?: emptyList()
        assertEquals(0, tracks.size)
    }

    @Test
    fun `build skips ssa subtitle tracks`() {
        val subtitles = listOf(Track("https://example.com/sub.ssa", "Japanese"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        val tracks = mediaInfo.mediaTracks ?: emptyList()
        assertEquals(0, tracks.size)
    }

    @Test
    fun `build includes mixed srt and vtt subtitles but skips ass`() {
        val subtitles = listOf(
            Track("https://example.com/sub_en.srt", "English"),
            Track("https://example.com/sub_fr.vtt", "French"),
            Track("https://example.com/sub_jp.ass", "Japanese"),
        )
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        val tracks = mediaInfo.mediaTracks ?: emptyList()
        assertEquals(2, tracks.size)
    }

    // ---- Metadata ----

    @Test
    fun `build includes metadata object`() {
        // MediaMetadata.getString() reads from Android Bundle which doesn't store values
        // in JVM unit tests. We verify the metadata object is attached (non-null).
        val video = createVideo("https://example.com/video.mp4")
        val mediaInfo = builder.build(video, testEpisode, testAnime)
        assertNotNull(mediaInfo.metadata)
    }

    // ---- Local file guard ----

    @Test
    fun `build throws for content scheme URLs`() {
        val video = createVideo("content://media/external/video/1234")
        assertThrows(IllegalStateException::class.java) {
            builder.build(video, testEpisode, testAnime)
        }
    }

    @Test
    fun `build throws for file scheme URLs`() {
        val video = createVideo("file:///sdcard/Download/episode.mp4")
        assertThrows(IllegalStateException::class.java) {
            builder.build(video, testEpisode, testAnime)
        }
    }
}
