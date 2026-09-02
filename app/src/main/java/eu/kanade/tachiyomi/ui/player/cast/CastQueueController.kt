package eu.kanade.tachiyomi.ui.player.cast

/**
 * One reading of the receiver's state, translated out of MediaStatus so this class stays testable.
 *
 * @param contentId The receiver's current item content id, or null when no media is loaded.
 *   The controller records progress only when this is non-null AND registered to an episode;
 *   early ticks before the receiver reports a known contentId are discarded.
 */
data class CastReceiverSnapshot(
    val contentId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isIdleFinished: Boolean,
)

/** What the controller asks the ViewModel to do. The ViewModel owns every library write. */
interface CastQueueSink {
    /** Record progress for [episodeId] exactly the way local playback records it. */
    fun onProgress(episodeId: Long, positionMs: Long, durationMs: Long)

    /** The receiver moved on; adopt [episodeId] as the current episode without reloading. */
    fun onCurrentEpisodeChanged(episodeId: Long)

    /** The receiver ran out of items; top the queue up or let playback end. */
    fun onQueueExhausted()
}

/**
 * Follows the Cast receiver through [CastReceiverSnapshot] readings.
 *
 * The receiver is the source of truth. This controller never pushes an advance command;
 * it reacts to the contentId, position, and idle-finished signals the receiver reports.
 * The [episodeIdByContentId] map ties the receiver's content ids to the episodes that
 * were queued, so progress and episode changes stay assigned to the right episode.
 */
class CastQueueController(
    private val sink: CastQueueSink,
) {

    private val episodeIdByContentId = LinkedHashMap<String, Long>()
    private var currentContentId: String? = null
    private var lastSnapshot: CastReceiverSnapshot? = null

    fun registerQueuedItem(contentId: String, episodeId: Long) {
        episodeIdByContentId[contentId] = episodeId
    }

    fun unregisterEpisode(episodeId: Long) {
        episodeIdByContentId.entries.removeAll { it.value == episodeId }
    }

    fun queuedEpisodeIds(): List<Long> = episodeIdByContentId.values.toList()

    fun contentIdForEpisode(episodeId: Long): String? =
        episodeIdByContentId.entries.firstOrNull { it.value == episodeId }?.key

    /**
     * Feeds one receiver reading into the state machine.
     * Finalizes the outgoing episode on item change, reports live progress, and
     * reports queue exhaustion on idle-finished.
     */
    fun onSnapshot(snapshot: CastReceiverSnapshot) {
        val newContentId = snapshot.contentId
        if (newContentId != null && newContentId != currentContentId) {
            finalizeAtFullDuration()
            val previousContentId = currentContentId
            currentContentId = newContentId
            // The app adopts the first item on load; only a change between two observed items is an advance.
            if (previousContentId != null) {
                episodeIdByContentId[newContentId]?.let { sink.onCurrentEpisodeChanged(it) }
            }
        }

        val episodeId = currentContentId?.let { episodeIdByContentId[it] }
        if (episodeId != null && snapshot.durationMs > 0L) {
            sink.onProgress(episodeId, snapshot.positionMs, snapshot.durationMs)
        }

        lastSnapshot = snapshot

        if (snapshot.isIdleFinished) {
            finalizeAtFullDuration()
            sink.onQueueExhausted()
        }
    }

    /** Finalizes the current episode at its last known position; called when the session ends. */
    fun onSessionEnded() {
        finalizeCurrentEpisodeAtLastPosition()
        clearState()
    }

    /** Clears all state; called before a fresh queue load. */
    fun reset() = clearState()

    private fun finalizeCurrentEpisodeAtLastPosition() {
        val snapshot = lastSnapshot ?: return
        val episodeId = snapshot.contentId?.let { episodeIdByContentId[it] } ?: return
        if (snapshot.durationMs <= 0L) return
        sink.onProgress(episodeId, snapshot.positionMs, snapshot.durationMs)
    }

    private fun finalizeAtFullDuration() {
        val snapshot = lastSnapshot ?: return
        val episodeId = snapshot.contentId?.let { episodeIdByContentId[it] } ?: return
        if (snapshot.durationMs <= 0L) return
        sink.onProgress(episodeId, snapshot.durationMs, snapshot.durationMs)
    }

    private fun clearState() {
        episodeIdByContentId.clear()
        currentContentId = null
        lastSnapshot = null
    }
}
