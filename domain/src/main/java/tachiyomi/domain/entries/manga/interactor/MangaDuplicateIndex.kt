package tachiyomi.domain.entries.manga.interactor

import tachiyomi.domain.entries.manga.model.DuplicateCandidate
import tachiyomi.domain.entries.manga.model.DuplicateConfidence
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.track.manga.model.MangaTrack
import tachiyomi.domain.util.TitleNormalizer

internal class MangaDuplicateIndex(library: List<Manga>, tracks: List<MangaTrack>) {

    private val tracksByMangaId = tracks.filter { it.remoteId > 0 }.groupBy { it.mangaId }

    private val byTracker = library
        .flatMap { manga -> trackerKeysOf(manga).map { key -> key to manga } }
        .groupBy({ it.first }, { it.second })

    private val byTitle = library.groupByNonBlank(::titleKey)

    private val byNormalizedTitle = library.groupByNonBlank(::normalizedTitleKey)

    fun duplicatesOf(manga: Manga): List<DuplicateCandidate> {
        val matches = listOf(
            DuplicateConfidence.TRACKER to trackerKeysOf(manga).flatMap { byTracker[it].orEmpty() },
            DuplicateConfidence.HIGH to byTitle[titleKey(manga)].orEmpty(),
            DuplicateConfidence.MEDIUM to byNormalizedTitle[normalizedTitleKey(manga)].orEmpty(),
        )
        return matches
            .flatMap { (confidence, duplicates) ->
                duplicates.map { DuplicateCandidate(winner = manga, loser = it, confidence = confidence) }
            }
            .filter { it.loser.id != manga.id }
            .distinctBy { it.loser.id }
    }

    private fun trackerKeysOf(manga: Manga): List<Pair<Long, Long>> =
        tracksByMangaId[manga.id].orEmpty().map { it.trackerId to it.remoteId }

    private fun titleKey(manga: Manga) = manga.title.lowercase()

    private fun normalizedTitleKey(manga: Manga) = TitleNormalizer.normalize(manga.title)

    private fun List<Manga>.groupByNonBlank(key: (Manga) -> String): Map<String, List<Manga>> =
        groupBy(key).filterKeys { it.isNotBlank() }
}
