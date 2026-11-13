package eu.kanade.tachiyomi.ui.player

import tachiyomi.domain.items.episode.model.Episode
import kotlin.math.abs

internal interface EpisodeProgressSink {
    fun patch(episodeId: Long, transform: (Episode) -> Episode): Episode?
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
        val update = record(episode, positionMs, totalMs) ?: return
        if (update.completed || claimTickSave(episode.id, positionMs)) sink.save(update.episode)

        val inDownloadRange = positionMs.toDouble() / totalMs > DOWNLOAD_AHEAD_FRACTION
        if (inDownloadRange && downloadAheadEpisodeId != episode.id) {
            downloadAheadEpisodeId = episode.id
            sink.downloadAhead()
        }
    }

    fun onCastProgress(episode: Episode, positionMs: Long, totalMs: Long) {
        if (totalMs <= 0L) return
        val update = record(episode, positionMs, totalMs) ?: return
        sink.save(update.episode)
    }

    private fun record(episode: Episode, positionMs: Long, totalMs: Long): RecordedProgress? {
        val completes = positionMs >= totalMs * completeFraction() &&
            shouldTrack() &&
            completedEpisodeId != episode.id
        val stored = sink.patch(episode.id) {
            it.copy(lastSecondSeen = positionMs, totalSeconds = totalMs, seen = it.seen || completes)
        } ?: return null

        if (completes) {
            completedEpisodeId = episode.id
            sink.complete(stored)
        }
        return RecordedProgress(stored, completes)
    }

    private class RecordedProgress(val episode: Episode, val completed: Boolean)

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
