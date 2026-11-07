package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

data class SavedWatchingPosition(val episode: Episode, val positionMillis: Long?)

fun List<EpisodeList.Item>.savedWatchingPosition(anime: Anime): SavedWatchingPosition? {
    val next = getNextUnseen(anime) ?: return null
    return when {
        next.lastSecondSeen > 0L -> SavedWatchingPosition(next, positionMillis = next.lastSecondSeen)
        any { it.episode.seen } -> SavedWatchingPosition(next, positionMillis = null)
        else -> null
    }
}
