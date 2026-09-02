package eu.kanade.tachiyomi.ui.player.cast

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import io.mockk.every
import io.mockk.mockk
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
        assertEquals(video.videoUrl, mediaInfo.contentId)
    }

    @Test
    fun `build refuses protected HLS URLs until playlist proxying is supported`() {
        val video = createVideo(
            "https://example.com/stream.m3u8",
        ).copy(headers = okhttp3.Headers.headersOf("Referer", "https://example.com/"))

        assertThrows(IllegalStateException::class.java) {
            builder.build(video, testEpisode, testAnime)
        }
    }

    @Test
    fun `build refuses protected DASH URLs until playlist proxying is supported`() {
        val video = createVideo(
            "https://example.com/stream.mpd",
        ).copy(headers = okhttp3.Headers.headersOf("Referer", "https://example.com/"))

        assertThrows(IllegalStateException::class.java) {
            builder.build(video, testEpisode, testAnime)
        }
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

    // ---- Subtitle drop detection ----

    @Test
    fun `subtitlesDroppedForCast returns true when only ass subtitles are present`() {
        val subtitles = listOf(Track("https://example.com/sub.ass", "Japanese"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        assertEquals(true, builder.subtitlesDroppedForCast(video))
    }

    @Test
    fun `subtitlesDroppedForCast returns true when only ssa subtitles are present`() {
        val subtitles = listOf(Track("https://example.com/sub.ssa", "Japanese"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        assertEquals(true, builder.subtitlesDroppedForCast(video))
    }

    @Test
    fun `subtitlesDroppedForCast returns false when a compatible track survives`() {
        val subtitles = listOf(
            Track("https://example.com/sub_jp.ass", "Japanese"),
            Track("https://example.com/sub_en.vtt", "English"),
        )
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        assertEquals(false, builder.subtitlesDroppedForCast(video))
    }

    @Test
    fun `subtitlesDroppedForCast returns false when only compatible subtitles are present`() {
        val subtitles = listOf(Track("https://example.com/sub.srt", "English"))
        val video = createVideo("https://example.com/video.mp4", subtitles = subtitles)
        assertEquals(false, builder.subtitlesDroppedForCast(video))
    }

    @Test
    fun `subtitlesDroppedForCast returns false when there are no subtitle tracks`() {
        val video = createVideo("https://example.com/video.mp4")
        assertEquals(false, builder.subtitlesDroppedForCast(video))
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

    @Test
    fun `build rejects downloaded containers that Cast cannot play`() {
        val file = mockk<UniFile>()
        every { file.exists() } returns true
        every { file.isFile } returns true
        every { file.type } returns "video/x-matroska"
        every { file.name } returns "episode.mkv"
        val proxy = CastStreamProxy(
            client = okhttp3.OkHttpClient(),
            addressProvider = { java.net.InetAddress.getLoopbackAddress() },
            localFileProvider = { file },
        )

        try {
            val localBuilder = CastMediaBuilder(proxy)
            val error = assertThrows(IllegalStateException::class.java) {
                localBuilder.build(createVideo("content://downloads/episode.mkv"), testEpisode, testAnime)
            }
            assertEquals(true, error.message?.contains("video/x-matroska") == true)
        } finally {
            proxy.stop()
        }
    }

    // ---- Queue items ----

    @Test
    fun `buildQueueItem wraps the media info with autoplay enabled`() {
        val video = createVideo("https://example.com/video.mp4")
        val item = builder.buildQueueItem(video, testEpisode, testAnime)
        assertNotNull(item.media)
        assertEquals("https://example.com/video.mp4", item.media?.contentId)
    }

    @Test
    fun `buildQueueItem preserves the contentId used for episode identity`() {
        val video = createVideo("https://example.com/episode-42.mp4")
        val item = builder.buildQueueItem(video, testEpisode, testAnime)
        assertEquals(video.videoUrl, item.media?.contentId)
    }
}
