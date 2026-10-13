package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkPlayList
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers.Companion.toHeaders
import java.util.concurrent.ConcurrentLinkedQueue

class LinkCollector {
    private val links = ConcurrentLinkedQueue<ExtractorLink>()
    private val subtitles = ConcurrentLinkedQueue<SubtitleFile>()

    fun addLink(link: ExtractorLink) {
        links += link
    }

    fun addSubtitle(subtitle: SubtitleFile) {
        subtitles += subtitle
    }

    fun links(): List<ExtractorLink> = links.toList()

    fun subtitles(): List<SubtitleFile> = subtitles.toList()
}

fun buildHosters(links: List<ExtractorLink>, subtitles: List<SubtitleFile>): List<Hoster> {
    val tracks = subtitles.map { Track(it.url, it.lang) }
    return links.groupBy { it.source }.mapNotNull { (source, group) ->
        group.mapNotNull { it.toVideo(tracks) }
            .takeIf { it.isNotEmpty() }
            ?.let { Hoster(hosterName = source, videoList = it.withDistinctTitles()) }
    }
}

private fun List<Video>.withDistinctTitles(): List<Video> {
    val repeated = groupingBy { it.videoTitle }.eachCount().filterValues { it > 1 }.keys
    val seen = mutableMapOf<String, Int>()
    return map { video ->
        if (video.videoTitle !in repeated) return@map video
        val number = seen.merge(video.videoTitle, 1, Int::plus)
        video.copy(videoTitle = "${video.videoTitle} #$number")
    }
}

fun ExtractorLink.toVideo(subtitles: List<Track>): Video? {
    if (this is ExtractorLinkPlayList || type in UNPLAYABLE_TYPES) return null
    return Video(
        videoUrl = url,
        videoTitle = listOf(name, Qualities.getStringByInt(quality)).filter { it.isNotBlank() }.joinToString(" - "),
        resolution = quality.takeIf { it > 0 && it != Qualities.Unknown.value },
        headers = getAllHeaders().toHeaders(),
        subtitleTracks = subtitles,
        audioTracks = audioTracks.mapIndexed { index, audio -> Track(audio.url, "Audio ${index + 1}") },
    )
}

private val UNPLAYABLE_TYPES = setOf(ExtractorLinkType.TORRENT, ExtractorLinkType.MAGNET)
