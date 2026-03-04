package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.DuplicateCandidate
import tachiyomi.domain.entries.anime.model.DuplicateConfidence
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.util.TitleNormalizer

class GetDuplicateLibraryAnime(
    private val animeRepository: AnimeRepository,
    private val getAnimeTracks: GetAnimeTracks,
) {

    suspend fun await(anime: Anime): List<Anime> {
        return animeRepository.getDuplicateLibraryAnime(anime.id, anime.title.lowercase())
    }

    suspend fun awaitAll(anime: Anime): List<DuplicateCandidate> {
        val results = mutableListOf<DuplicateCandidate>()
        val seen = mutableSetOf<Long>()

        // 1. Tracker ID match (highest confidence)
        getAnimeTracks.await(anime.id).forEach { track ->
            if (track.remoteId > 0) {
                animeRepository.getDuplicateLibraryAnimeByTracker(
                    track.trackerId,
                    track.remoteId,
                    anime.id,
                ).forEach { duplicate ->
                    if (seen.add(duplicate.id)) {
                        results += DuplicateCandidate(winner = anime, loser = duplicate, confidence = DuplicateConfidence.TRACKER)
                    }
                }
            }
        }

        // 2. Exact title match
        animeRepository.getDuplicateLibraryAnime(anime.id, anime.title.lowercase())
            .forEach { duplicate ->
                if (seen.add(duplicate.id)) {
                    results += DuplicateCandidate(winner = anime, loser = duplicate, confidence = DuplicateConfidence.HIGH)
                }
            }

        // 3. Normalized title match
        val normalized = TitleNormalizer.normalize(anime.title)
        animeRepository.getDuplicateLibraryAnimeByNormalizedTitle(normalized, anime.id)
            .forEach { duplicate ->
                if (seen.add(duplicate.id)) {
                    results += DuplicateCandidate(winner = anime, loser = duplicate, confidence = DuplicateConfidence.MEDIUM)
                }
            }

        return results
    }
}
