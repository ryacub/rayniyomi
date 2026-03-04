package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.repository.MangaRepository

class MergeLibraryManga(
    private val mangaRepository: MangaRepository,
) {
    /** Merges [deleteId] into [keepId]. [deleteId] is removed after merge. */
    suspend fun await(keepId: Long, deleteId: Long) {
        mangaRepository.mergeEntries(keepId, deleteId)
    }
}
