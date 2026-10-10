package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.DuplicateCandidate
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

class GetDuplicateLibraryManga(
    private val mangaRepository: MangaRepository,
    private val getMangaTracks: GetMangaTracks,
) {

    suspend fun await(manga: Manga): List<Manga> {
        return mangaRepository.getDuplicateLibraryManga(manga.id, manga.title.lowercase())
    }

    suspend fun awaitAll(manga: Manga): List<DuplicateCandidate> {
        return index(mangaRepository.getMangaFavorites()).duplicatesOf(manga)
    }

    suspend fun awaitPairs(library: List<Manga>): List<DuplicateCandidate> {
        val index = index(library)
        return library
            .flatMap(index::duplicatesOf)
            .distinctBy { setOf(it.winner.id, it.loser.id) }
    }

    private suspend fun index(library: List<Manga>) = MangaDuplicateIndex(library, getMangaTracks.awaitAll())
}
