package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.DuplicateCandidate
import tachiyomi.domain.entries.manga.model.DuplicateConfidence
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.util.TitleNormalizer

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
    private val getMangaTracks: GetMangaTracks,
) {

    suspend fun await(manga: Manga): List<Manga> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
    }

    suspend fun awaitAll(manga: Manga): List<DuplicateCandidate> {
        val results = mutableListOf<DuplicateCandidate>()
        val seen = mutableSetOf<Long>()

        // 1. Tracker ID match (highest confidence)
        getMangaTracks.await(manga.id).forEach { track ->
            if (track.remoteId > 0) {
                mangaRepository.getDuplicateLibraryMangaByTracker(
                    track.trackerId,
                    track.remoteId,
                    manga.id,
                ).forEach { duplicate ->
                    if (seen.add(duplicate.id)) {
                        results +=
                            DuplicateCandidate(
                                winner = manga,
                                loser = duplicate,
                                confidence = DuplicateConfidence.TRACKER,
                            )
                    }
                }
            }
        }

        // 2. Exact title match
        mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
            .forEach { duplicate ->
                if (seen.add(duplicate.id)) {
                    results +=
                        DuplicateCandidate(winner = manga, loser = duplicate, confidence = DuplicateConfidence.HIGH)
                }
            }

        // 3. Normalized title match
        val normalized = TitleNormalizer.normalize(manga.title)
        mangaRepository.getDuplicateLibraryMangaByNormalizedTitle(normalized, manga.id)
            .forEach { duplicate ->
                if (seen.add(duplicate.id)) {
                    results +=
                        DuplicateCandidate(winner = manga, loser = duplicate, confidence = DuplicateConfidence.MEDIUM)
                }
            }

        return results
    }
}
