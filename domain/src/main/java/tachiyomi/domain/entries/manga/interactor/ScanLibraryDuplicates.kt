package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.DuplicateCandidate

class ScanLibraryDuplicates(
    private val getLibraryManga: GetLibraryManga,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
) {

    /** Returns all duplicate pairs found across the library. Each pair appears only once. */
    suspend fun await(): List<DuplicateCandidate> {
        val library = getLibraryManga.await().map { it.manga }.distinctBy { it.id }
        return getDuplicateLibraryManga.awaitPairs(library)
    }
}
