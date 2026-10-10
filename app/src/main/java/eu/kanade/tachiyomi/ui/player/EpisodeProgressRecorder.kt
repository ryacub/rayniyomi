package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.data.database.models.anime.Episode
import kotlin.math.abs

internal interface EpisodeProgressSink {
    fun save(episode: Episode)
    fun complete(episode: Episode)
    fun downloadAhead()
}

internal class EpisodeProgressRecorder(
    private val sink: EpisodeProgressSink,
    private val completeFraction: () -> Float,
    private val shouldTrack: () -> Boolean,
    private val saveIntervalMs: Long = 10_000L,
) {

    private var completedEpisodeId: Long? = null
    private var downloadAheadEpisodeId: Long? = null
    private var savedEpisodeId: Long? = null
    private var savedPositionMs = 0L

    fun onLocalTick(episode: Episode, positionMs: Long, totalMs: Long) {
        if (totalMs <= 0L) return
        val completed = record(episode, positionMs, totalMs)
        if (completed || claimTickSave(episode.id, positionMs)) sink.save(episode)

        val inDownloadRange = positionMs.toDouble() / totalMs > DOWNLOAD_AHEAD_FRACTION
        if (inDownloadRange && downloadAheadEpisodeId != episode.id) {
            downloadAheadEpisodeId = episode.id
            sink.downloadAhead()
        }
    }

    fun onCastProgress(episode: Episode, positionMs: Long, totalMs: Long) {
        if (totalMs <= 0L) return
        record(episode, positionMs, totalMs)
        sink.save(episode)
    }

    private fun record(episode: Episode, positionMs: Long, totalMs: Long): Boolean {
        episode.last_second_seen = positionMs
        episode.total_seconds = totalMs

        if (positionMs < totalMs * completeFraction() || !shouldTrack()) return false
        if (completedEpisodeId == episode.id) return false
        completedEpisodeId = episode.id
        episode.seen = true
        sink.complete(episode)
        return true
    }

    private fun claimTickSave(episodeId: Long, positionMs: Long): Boolean {
        if (episodeId == savedEpisodeId && abs(positionMs - savedPositionMs) < saveIntervalMs) return false
        savedEpisodeId = episodeId
        savedPositionMs = positionMs
        return true
    }

    private companion object {
        const val DOWNLOAD_AHEAD_FRACTION = 0.35
    }
}
