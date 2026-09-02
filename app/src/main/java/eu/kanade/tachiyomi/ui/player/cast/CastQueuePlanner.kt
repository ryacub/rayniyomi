package eu.kanade.tachiyomi.ui.player.cast

import eu.kanade.tachiyomi.data.database.models.anime.Episode

/**
 * Chooses which episodes belong in the Cast queue.
 *
 * The current episode always plays even when already seen, because the user picked it.
 * Everything after it is filtered to unseen episodes so casting never replays finished ones.
 */
object CastQueuePlanner {

    /**
     * Upcoming unseen episodes after [currentEpisodeId], at most [lookahead] of them.
     * Empty when the current episode is unknown or last in the playlist.
     */
    fun planLookahead(
        playlist: List<Episode>,
        currentEpisodeId: Long,
        lookahead: Int = CAST_QUEUE_LOOKAHEAD,
    ): List<Episode> {
        val index = playlist.indexOfFirst { it.id == currentEpisodeId }
        if (index == -1) return emptyList()
        return playlist.drop(index + 1).filterNot { it.seen }.take(lookahead)
    }

    /** Ids of already-queued episodes that have since been marked seen and should be dropped. */
    fun staleEpisodeIds(queuedEpisodeIds: List<Long>, playlist: List<Episode>, currentEpisodeId: Long): List<Long> {
        val seenIds = playlist.filter { it.seen }.map { it.id }.toSet()
        return queuedEpisodeIds.filter { it != currentEpisodeId && it in seenIds }
    }
}

/**
 * How many episodes past the current one to resolve and queue ahead of the receiver.
 *
 * Each queue item needs a resolved video URL, which costs one hoster fetch plus extractor
 * round trips, so resolving the whole season would add that to cast start latency. The
 * receiver needs only one item ahead to advance without a gap; two gives slack for a user
 * skip and one failed resolution. Extension URLs are often signed and short-lived, so a
 * deeper window mostly resolves URLs that expire before they play.
 */
const val CAST_QUEUE_LOOKAHEAD = 2
