package eu.kanade.tachiyomi.ui.entries.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.domain.track.manga.model.toDbTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.Tracker
import kotlinx.collections.immutable.ImmutableList
import java.io.Serializable
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

sealed interface EntryTrack : Serializable {
    val entryId: Long
    val trackerId: Long
    val title: String
    val remoteUrl: String
    val status: Long
    val progress: Double
    val total: Long
    val score: Double
    val startDate: Long
    val finishDate: Long
    val private: Boolean

    data class Manga(val track: DomainMangaTrack) : EntryTrack {
        override val entryId get() = track.mangaId
        override val trackerId get() = track.trackerId
        override val title get() = track.title
        override val remoteUrl get() = track.remoteUrl
        override val status get() = track.status
        override val progress get() = track.lastChapterRead
        override val total get() = track.totalChapters
        override val score get() = track.score
        override val startDate get() = track.startDate
        override val finishDate get() = track.finishDate
        override val private get() = track.private
    }

    data class Anime(val track: DomainAnimeTrack) : EntryTrack {
        override val entryId get() = track.animeId
        override val trackerId get() = track.trackerId
        override val title get() = track.title
        override val remoteUrl get() = track.remoteUrl
        override val status get() = track.status
        override val progress get() = track.lastEpisodeSeen
        override val total get() = track.totalEpisodes
        override val score get() = track.score
        override val startDate get() = track.startDate
        override val finishDate get() = track.finishDate
        override val private get() = track.private
    }
}

fun Tracker.statusList(track: EntryTrack): List<Long> = when (track) {
    is EntryTrack.Manga -> mangaService.getStatusListManga()
    is EntryTrack.Anime -> animeService.getStatusListAnime()
}

fun Tracker.statusName(track: EntryTrack, status: Long): StringResource? = when (track) {
    is EntryTrack.Manga -> (this as? MangaTracker)?.getStatusForManga(status)
    is EntryTrack.Anime -> (this as? AnimeTracker)?.getStatusForAnime(status)
}

fun Tracker.scoreList(track: EntryTrack): ImmutableList<String> = when (track) {
    is EntryTrack.Manga -> mangaService.getScoreList()
    is EntryTrack.Anime -> animeService.getScoreList()
}

fun Tracker.displayScore(track: EntryTrack): String = when (track) {
    is EntryTrack.Manga -> mangaService.displayScore(track.track)
    is EntryTrack.Anime -> animeService.displayScore(track.track)
}

suspend fun Tracker.setRemoteStatus(track: EntryTrack, status: Long) = when (track) {
    is EntryTrack.Manga -> mangaService.setRemoteMangaStatus(track.track.toDbTrack(), status)
    is EntryTrack.Anime -> animeService.setRemoteAnimeStatus(track.track.toDbTrack(), status)
}

suspend fun Tracker.setRemoteProgress(track: EntryTrack, progress: Int) = when (track) {
    is EntryTrack.Manga -> mangaService.setRemoteLastChapterRead(track.track.toDbTrack(), progress)
    is EntryTrack.Anime -> animeService.setRemoteLastEpisodeSeen(track.track.toDbTrack(), progress)
}

suspend fun Tracker.setRemoteScore(track: EntryTrack, score: String) = when (track) {
    is EntryTrack.Manga -> mangaService.setRemoteScore(track.track.toDbTrack(), score)
    is EntryTrack.Anime -> animeService.setRemoteScore(track.track.toDbTrack(), score)
}

suspend fun Tracker.setRemoteStartDate(track: EntryTrack, epochMillis: Long) = when (track) {
    is EntryTrack.Manga -> mangaService.setRemoteStartDate(track.track.toDbTrack(), epochMillis)
    is EntryTrack.Anime -> animeService.setRemoteStartDate(track.track.toDbTrack(), epochMillis)
}

suspend fun Tracker.setRemoteFinishDate(track: EntryTrack, epochMillis: Long) = when (track) {
    is EntryTrack.Manga -> mangaService.setRemoteFinishDate(track.track.toDbTrack(), epochMillis)
    is EntryTrack.Anime -> animeService.setRemoteFinishDate(track.track.toDbTrack(), epochMillis)
}

suspend fun Tracker.setRemotePrivate(track: EntryTrack, private: Boolean) {
    when (track) {
        is EntryTrack.Manga -> (this as? MangaTracker)?.setRemotePrivate(track.track.toDbTrack(), private)
        is EntryTrack.Anime -> (this as? AnimeTracker)?.setRemotePrivate(track.track.toDbTrack(), private)
    }
}

fun Tracker.isDeletable(track: EntryTrack): Boolean = when (track) {
    is EntryTrack.Manga -> this is DeletableMangaTracker
    is EntryTrack.Anime -> this is DeletableAnimeTracker
}

suspend fun Tracker.deleteRemote(track: EntryTrack) = when (track) {
    is EntryTrack.Manga -> (this as DeletableMangaTracker).delete(track.track)
    is EntryTrack.Anime -> (this as DeletableAnimeTracker).delete(track.track)
}

suspend fun Tracker.register(item: EntryTrackSearch, entryId: Long) = when (item) {
    is EntryTrackSearch.Manga -> mangaService.register(item.search, entryId)
    is EntryTrackSearch.Anime -> animeService.register(item.search, entryId)
}
