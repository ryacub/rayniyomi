package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

fun List<EpisodeList.Item>.resumeEpisode(anime: Anime): Episode? {
    val next = getNextUnseen(anime) ?: return null
    return next.takeIf { it.lastSecondSeen > 0L || any { item -> item.episode.seen } }
}
