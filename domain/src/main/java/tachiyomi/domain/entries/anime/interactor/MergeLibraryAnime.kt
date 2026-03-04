package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.AnimeRepository

class MergeLibraryAnime(
    private val animeRepository: AnimeRepository,
) {
    /** Merges [deleteId] into [keepId]. [deleteId] is removed after merge. */
    suspend fun await(keepId: Long, deleteId: Long) {
        animeRepository.mergeEntries(keepId, deleteId)
    }
}
