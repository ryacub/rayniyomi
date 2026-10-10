package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.DuplicateCandidate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks

class GetDuplicateLibraryAnime(
    private val animeRepository: AnimeRepository,
    private val getAnimeTracks: GetAnimeTracks,
) {

    suspend fun await(anime: Anime): List<Anime> {
        return animeRepository.getDuplicateLibraryAnime(anime.id, anime.title.lowercase())
    }

    suspend fun awaitAll(anime: Anime): List<DuplicateCandidate> {
        return AnimeDuplicateIndex(animeRepository.getAnimeFavorites(), getAnimeTracks.awaitAll()).duplicatesOf(anime)
    }
}
