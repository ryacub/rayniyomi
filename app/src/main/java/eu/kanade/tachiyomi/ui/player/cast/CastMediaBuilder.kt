package eu.kanade.tachiyomi.ui.player.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * Builds a Cast [MediaInfo] from a [Video], [Episode], and [Anime].
 * Note: Cast SDK does not support custom HTTP headers; auth-required streams will fail without a proxy.
 */
class CastMediaBuilder {

    fun build(
        video: Video,
        episode: Episode,
        anime: Anime,
    ): MediaInfo {
        val videoUrl = video.videoUrl

        check(!videoUrl.startsWith("content://") && !videoUrl.startsWith("file://")) {
            "Cannot cast local files: $videoUrl"
        }

        val contentType = when {
            videoUrl.contains(".m3u8") -> "application/x-mpegURL"
            videoUrl.contains(".mpd") -> "application/dash+xml"
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

    // ass/ssa use a vector rendering engine Chromecast doesn't support
    private fun Track.isCastCompatible(): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".srt") || lower.endsWith(".vtt")
    }
}
