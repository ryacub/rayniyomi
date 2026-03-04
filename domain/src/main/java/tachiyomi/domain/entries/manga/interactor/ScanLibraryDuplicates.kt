package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.DuplicateCandidate

class ScanLibraryDuplicates(
    private val getLibraryManga: GetLibraryManga,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
) {

    /** Returns all duplicate pairs found across the library. Each pair appears only once. */
    suspend fun await(): List<DuplicateCandidate> {
        val allManga = getLibraryManga.await().map { it.manga }
        val seen = mutableSetOf<Pair<Long, Long>>()
        val results = mutableListOf<DuplicateCandidate>()

        for (manga in allManga) {
            val candidates = getDuplicateLibraryManga.awaitAll(manga)
            for (candidate in candidates) {
                val key = minOf(candidate.winner.id, candidate.loser.id) to maxOf(candidate.winner.id, candidate.loser.id)
                if (seen.add(key)) {
                    results += candidate
                }
            }
        }

        return results
    }
}
