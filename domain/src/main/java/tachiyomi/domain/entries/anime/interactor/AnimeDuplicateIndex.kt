package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.DuplicateCandidate
import tachiyomi.domain.entries.anime.model.DuplicateConfidence
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.util.TitleNormalizer

internal class AnimeDuplicateIndex(library: List<Anime>, tracks: List<AnimeTrack>) {

    private val tracksByAnimeId = tracks.filter { it.remoteId > 0 }.groupBy { it.animeId }

    private val byTracker = library
        .flatMap { anime -> trackerKeysOf(anime).map { key -> key to anime } }
        .groupBy({ it.first }, { it.second })

    private val byTitle = library.groupByNonBlank(::titleKey)

    private val byNormalizedTitle = library.groupByNonBlank(::normalizedTitleKey)

    fun duplicatesOf(anime: Anime): List<DuplicateCandidate> {
        val matches = listOf(
            DuplicateConfidence.TRACKER to trackerKeysOf(anime).flatMap { byTracker[it].orEmpty() },
            DuplicateConfidence.HIGH to byTitle[titleKey(anime)].orEmpty(),
            DuplicateConfidence.MEDIUM to byNormalizedTitle[normalizedTitleKey(anime)].orEmpty(),
        )
        return matches
            .flatMap { (confidence, duplicates) ->
                duplicates.map { DuplicateCandidate(winner = anime, loser = it, confidence = confidence) }
            }
            .filter { it.loser.id != anime.id }
            .distinctBy { it.loser.id }
    }

    private fun trackerKeysOf(anime: Anime): List<Pair<Long, Long>> =
        tracksByAnimeId[anime.id].orEmpty().map { it.trackerId to it.remoteId }

    private fun titleKey(anime: Anime) = anime.title.lowercase()

    private fun normalizedTitleKey(anime: Anime) = TitleNormalizer.normalize(anime.title)

    private fun List<Anime>.groupByNonBlank(key: (Anime) -> String): Map<String, List<Anime>> =
        groupBy(key).filterKeys { it.isNotBlank() }
}
