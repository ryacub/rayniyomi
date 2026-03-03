package eu.kanade.domain.track.interactor

import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack
import kotlin.math.max

class TrackSyncConflictResolver {

    data class MangaResolution(
        val mergedTrack: MangaTrack,
        val pushRemoteTrack: MangaTrack?,
        val chapterUpdates: List<ChapterUpdate>,
    )

    data class AnimeResolution(
        val mergedTrack: AnimeTrack,
        val pushRemoteTrack: AnimeTrack?,
        val episodeUpdates: List<EpisodeUpdate>,
    )

    fun resolveManga(
        localTrack: MangaTrack,
        remoteTrack: MangaTrack,
        chapters: List<Chapter>,
    ): MangaResolution {
        val localContinuousRead = chapters
            .asSequence()
            .filter { it.isRecognizedNumber }
            .sortedBy { it.chapterNumber }
            .takeWhile { it.read }
            .lastOrNull()
            ?.chapterNumber
            ?: 0.0
        val localProgress = max(localTrack.lastChapterRead, localContinuousRead)
        val remoteProgress = remoteTrack.lastChapterRead
        val winningProgress = max(localProgress, remoteProgress)

        val chapterUpdates = chapters
            .asSequence()
            .filter { it.isRecognizedNumber }
            .filter { !it.read }
            .filter { it.chapterNumber <= winningProgress }
            .map { ChapterUpdate(id = it.id, read = true) }
            .toList()

        val mergedTrack = localTrack.copy(
            remoteId = remoteTrack.remoteId,
            libraryId = remoteTrack.libraryId ?: localTrack.libraryId,
            title = remoteTrack.title.ifBlank { localTrack.title },
            totalChapters = max(localTrack.totalChapters, remoteTrack.totalChapters),
            remoteUrl = remoteTrack.remoteUrl.ifBlank { localTrack.remoteUrl },
            lastChapterRead = winningProgress,
            status = if (remoteProgress >= localProgress) remoteTrack.status else localTrack.status,
            score = if (remoteProgress >= localProgress) remoteTrack.score else localTrack.score,
            startDate = if (remoteProgress >= localProgress) remoteTrack.startDate else localTrack.startDate,
            finishDate = if (remoteProgress >= localProgress) remoteTrack.finishDate else localTrack.finishDate,
            private = remoteTrack.private,
        )

        val pushRemoteTrack = if (localProgress > remoteProgress) {
            mergedTrack
        } else {
            null
        }

        return MangaResolution(
            mergedTrack = mergedTrack,
            pushRemoteTrack = pushRemoteTrack,
            chapterUpdates = chapterUpdates,
        )
    }

    fun resolveAnime(
        localTrack: AnimeTrack,
        remoteTrack: AnimeTrack,
        episodes: List<Episode>,
    ): AnimeResolution {
        val localContinuousSeen = episodes
            .asSequence()
            .filter { it.isRecognizedNumber }
            .sortedBy { it.episodeNumber }
            .takeWhile { it.seen }
            .lastOrNull()
            ?.episodeNumber
            ?: 0.0
        val localProgress = max(localTrack.lastEpisodeSeen, localContinuousSeen)
        val remoteProgress = remoteTrack.lastEpisodeSeen
        val winningProgress = max(localProgress, remoteProgress)

        val episodeUpdates = episodes
            .asSequence()
            .filter { it.isRecognizedNumber }
            .filter { !it.seen }
            .filter { it.episodeNumber <= winningProgress }
            .map { EpisodeUpdate(id = it.id, seen = true) }
            .toList()

        val mergedTrack = localTrack.copy(
            remoteId = remoteTrack.remoteId,
            libraryId = remoteTrack.libraryId ?: localTrack.libraryId,
            title = remoteTrack.title.ifBlank { localTrack.title },
            totalEpisodes = max(localTrack.totalEpisodes, remoteTrack.totalEpisodes),
            remoteUrl = remoteTrack.remoteUrl.ifBlank { localTrack.remoteUrl },
            lastEpisodeSeen = winningProgress,
            status = if (remoteProgress >= localProgress) remoteTrack.status else localTrack.status,
            score = if (remoteProgress >= localProgress) remoteTrack.score else localTrack.score,
            startDate = if (remoteProgress >= localProgress) remoteTrack.startDate else localTrack.startDate,
            finishDate = if (remoteProgress >= localProgress) remoteTrack.finishDate else localTrack.finishDate,
            private = remoteTrack.private,
        )

        val pushRemoteTrack = if (localProgress > remoteProgress) {
            mergedTrack
        } else {
            null
        }

        return AnimeResolution(
            mergedTrack = mergedTrack,
            pushRemoteTrack = pushRemoteTrack,
            episodeUpdates = episodeUpdates,
        )
    }
}
