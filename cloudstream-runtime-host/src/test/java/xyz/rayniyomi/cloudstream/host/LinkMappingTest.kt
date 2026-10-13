package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.newAudioFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import eu.kanade.tachiyomi.animesource.model.Track
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

class LinkMappingTest {

    private suspend fun link(
        source: String = "Vidtube",
        url: String = "https://cdn.test/$source.m3u8",
        type: ExtractorLinkType = ExtractorLinkType.M3U8,
        quality: Int = 720,
        referer: String = "https://site.test/",
        headers: Map<String, String> = emptyMap(),
    ): ExtractorLink = newExtractorLink(source, source, url, type) {
        this.quality = quality
        this.referer = referer
        this.headers = headers
    }

    @Test
    fun `a link becomes a video with its quality and referer`() = runTest {
        val video = link().toVideo(emptyList())!!

        video.videoUrl shouldBe "https://cdn.test/Vidtube.m3u8"
        video.videoTitle shouldBe "Vidtube - 720p"
        video.resolution shouldBe 720
        video.headers?.get("Referer") shouldBe "https://site.test/"
    }

    @Test
    fun `an existing referer header is not replaced`() = runTest {
        val video = link(headers = mapOf("referer" to "https://own.test/")).toVideo(emptyList())!!

        video.headers?.values("Referer") shouldContainExactly listOf("https://own.test/")
    }

    @Test
    fun `unknown and auto quality have no resolution`() = runTest {
        val unknown = link(quality = Qualities.Unknown.value).toVideo(emptyList())!!
        val auto = link(quality = 0).toVideo(emptyList())!!

        unknown.resolution shouldBe null
        unknown.videoTitle shouldBe "Vidtube"
        auto.resolution shouldBe null
        auto.videoTitle shouldBe "Vidtube - Auto"
    }

    @Test
    fun `torrent and magnet links are dropped`() = runTest {
        link(type = ExtractorLinkType.TORRENT).toVideo(emptyList()) shouldBe null
        link(type = ExtractorLinkType.MAGNET).toVideo(emptyList()) shouldBe null
    }

    @Test
    fun `links group into one hoster per source with subtitles on every video`() = runTest {
        val subtitle = newSubtitleFile("Arabic", "https://cdn.test/ar.vtt")
        val hosters = buildHosters(
            listOf(link("A", quality = 1080), link("B"), link("A", url = "https://cdn.test/a2.mp4", quality = 480)),
            listOf(subtitle),
        )

        hosters.map { it.hosterName } shouldContainExactly listOf("A", "B")
        hosters.first().videoList.orEmpty().map { it.resolution } shouldContainExactly listOf(1080, 480)
        hosters.flatMap { it.videoList.orEmpty() }.forEach {
            it.subtitleTracks shouldContainExactly listOf(Track("https://cdn.test/ar.vtt", "Arabic"))
        }
    }

    @Test
    fun `videos that share a title in one hoster are numbered`() = runTest {
        val unknown = Qualities.Unknown.value
        val hosters = buildHosters(
            listOf(
                link("A", url = "https://cdn.test/1.mp4", quality = unknown),
                link("A", url = "https://cdn.test/2.mp4", quality = unknown),
                link("A", url = "https://cdn.test/3.mp4", quality = 720),
                link("B", quality = unknown),
            ),
            emptyList(),
        )

        hosters.first().videoList.orEmpty().map { it.videoTitle } shouldContainExactly
            listOf("A #1", "A #2", "A - 720p")
        hosters.last().videoList.orEmpty().map { it.videoTitle } shouldContainExactly listOf("B")
    }

    @Test
    fun `a source with only dropped links gives no hoster`() = runTest {
        buildHosters(listOf(link("T", type = ExtractorLinkType.TORRENT)), emptyList()) shouldBe emptyList()
    }

    @Test
    fun `separate audio tracks are kept`() = runTest {
        val audio = newAudioFile("https://cdn.test/audio.m4a")
        val withAudio = link().apply { audioTracks = listOf(audio) }

        withAudio.toVideo(emptyList())!!.audioTracks.map { it.url } shouldContainExactly
            listOf("https://cdn.test/audio.m4a")
    }

    @Test
    fun `the collector keeps every link added from parallel callbacks`() = runTest {
        val collector = LinkCollector()
        val sample = link()

        withContext(Dispatchers.Default) {
            repeat(2_000) { launch { collector.addLink(sample) } }
        }

        collector.links().size shouldBe 2_000
    }
}
