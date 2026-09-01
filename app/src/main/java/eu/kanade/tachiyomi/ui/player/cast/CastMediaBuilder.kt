package eu.kanade.tachiyomi.ui.player.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * Builds a Cast [MediaInfo] from a [Video], [Episode], and [Anime].
 * Header-dependent progressive streams use the local proxy; protected HLS and DASH remain unsupported.
 */
class CastMediaBuilder(
    private val streamProxy: CastStreamProxy? = null,
) {

    fun build(
        video: Video,
        episode: Episode,
        anime: Anime,
        requestHeaders: Headers? = video.headers,
    ): MediaInfo {
        val originalVideoUrl = video.videoUrl
        val headers = requestHeaders
        val localMedia = if (isLocalUri(originalVideoUrl)) {
            streamProxy?.localMediaFor(originalVideoUrl)
                ?: error("Cannot cast a downloaded video without a local proxy")
        } else {
            null
        }
        val videoUrl = localMedia?.url ?: when {
            headers == null || headers.size == 0 -> originalVideoUrl
            isAdaptiveStream(originalVideoUrl) -> error("Cannot cast a protected HLS or DASH stream")
            streamProxy != null -> streamProxy.urlFor(originalVideoUrl, headers)
            else -> error("Cannot cast a stream with request headers")
        }

        val contentType = localMedia?.contentType ?: when {
            originalVideoUrl.contains(".m3u8") -> "application/x-mpegURL"
            originalVideoUrl.contains(".mpd") -> "application/dash+xml"
            else -> "video/mp4"
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, episode.name)
            putString(MediaMetadata.KEY_SERIES_TITLE, anime.title)
        }

        return MediaInfo.Builder(videoUrl)
            .setContentType(contentType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .setMediaTracks(buildMediaTracks(video))
            .build()
    }

    private fun buildMediaTracks(video: Video): List<MediaTrack> {
        val subtitleTracks = video.subtitleTracks
            .filter { it.isCastCompatible() }
            .mapIndexed { index, track ->
                MediaTrack.Builder(index.toLong(), MediaTrack.TYPE_TEXT)
                    .setContentId(track.url)
                    .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                    .setName(track.lang)
                    .build()
            }

        val audioTracks = video.audioTracks
            .mapIndexed { index, track ->
                MediaTrack.Builder((subtitleTracks.size + index).toLong(), MediaTrack.TYPE_AUDIO)
                    .setContentId(track.url)
                    .setName(track.lang)
                    .build()
            }

        return subtitleTracks + audioTracks
    }

    private fun isAdaptiveStream(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains(".m3u8") || lowerUrl.contains(".mpd")
    }

    private fun isLocalUri(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.startsWith("content://") || lowerUrl.startsWith("file://")
    }

    // ass/ssa use a vector rendering engine Chromecast doesn't support
    private fun Track.isCastCompatible(): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".srt") || lower.endsWith(".vtt")
    }
}
