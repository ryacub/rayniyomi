package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.ChapterType
import tachiyomi.domain.track.anime.model.AnimeTrack

internal enum class AniSkipTrackerKind {
    MAL,
    ANILIST,
    OTHER,
}

internal fun shouldAutoSkipSegment(
    chapterType: ChapterType,
    autoSkipOpening: Boolean,
    autoSkipEnding: Boolean,
): Boolean {
    return when (chapterType) {
        ChapterType.Opening, ChapterType.MixedOp -> autoSkipOpening
        ChapterType.Ending, ChapterType.Recap -> autoSkipEnding
        ChapterType.Other -> false
    }
}

internal fun resolveMalId(
    tracks: List<AnimeTrack>,
    trackerKindForId: (Long) -> AniSkipTrackerKind,
    anilistToMalResolver: (Long) -> Long,
): Long? {
    tracks.firstOrNull { trackerKindForId(it.trackerId) == AniSkipTrackerKind.MAL && it.remoteId > 0L }
        ?.let { return it.remoteId }

    tracks.firstOrNull { trackerKindForId(it.trackerId) == AniSkipTrackerKind.ANILIST && it.remoteId > 0L }
        ?.let { track ->
            val malId = anilistToMalResolver(track.remoteId)
            if (malId > 0L) {
                return malId
            }
        }

    return null
}
