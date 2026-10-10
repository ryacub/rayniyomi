package eu.kanade.tachiyomi.ui.player

import kotlin.math.abs

internal class PlaybackTickPolicy(private val saveIntervalMs: Long = 10_000L) {

    private var completedEpisodeId: Long? = null
    private var downloadAheadEpisodeId: Long? = null
    private var savedEpisodeId: Long? = null
    private var savedPositionMs = 0L

    fun claimCompletion(episodeId: Long): Boolean {
        if (completedEpisodeId == episodeId) return false
        completedEpisodeId = episodeId
        return true
    }

    fun claimDownloadAhead(episodeId: Long): Boolean {
        if (downloadAheadEpisodeId == episodeId) return false
        downloadAheadEpisodeId = episodeId
        return true
    }

    fun claimTickSave(episodeId: Long, positionMs: Long): Boolean {
        if (episodeId == savedEpisodeId && abs(positionMs - savedPositionMs) < saveIntervalMs) return false
        savedEpisodeId = episodeId
        savedPositionMs = positionMs
        return true
    }
}
