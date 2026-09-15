package eu.kanade.domain.items.episode.interactor

import eu.kanade.tachiyomi.data.filler.AnimeFillerSource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.library.service.LibraryPreferences

class PopulateFillerMarks(
    private val source: AnimeFillerSource,
    private val updateEpisode: UpdateEpisode,
    private val libraryPreferences: LibraryPreferences,
) {

    suspend fun await(anime: Anime, episodes: List<Episode>) {
        if (!libraryPreferences.autoPopulateAnimeFillermarks().get()) return

        val fillerEpisodes = source.getFillerEpisodes(anime.title) ?: return
        val updates = episodes
            .filter { !it.fillermark }
            .filter { it.episodeNumber in fillerEpisodes }
            .map { EpisodeUpdate(id = it.id, fillermark = true) }
        if (updates.isNotEmpty()) updateEpisode.awaitAll(updates)
    }
}
