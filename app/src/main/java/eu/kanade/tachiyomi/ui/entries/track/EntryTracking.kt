package eu.kanade.tachiyomi.ui.entries.track

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.Tracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

enum class EntryType { MANGA, ANIME }

@Inject
class EntryTrackings(
    private val manga: MangaEntryTracking,
    private val anime: AnimeEntryTracking,
) {
    fun of(type: EntryType): EntryTracking = when (type) {
        EntryType.MANGA -> manga
        EntryType.ANIME -> anime
    }

    fun of(track: EntryTrack): EntryTracking = when (track) {
        is EntryTrack.Manga -> manga
        is EntryTrack.Anime -> anime
    }
}

interface EntryTracking {
    fun subscribeTracks(entryId: Long): Flow<List<EntryTrack>>
    suspend fun refreshTracks(entryId: Long): List<Pair<Tracker?, Throwable>>
    fun supports(tracker: Tracker): Boolean
    fun accepts(tracker: Tracker, sourceId: Long): Boolean
    fun isEnhanced(tracker: Tracker): Boolean

    /** Throws when the tracker finds no match. */
    suspend fun registerEnhanced(tracker: Tracker, entryId: Long)
    suspend fun search(tracker: Tracker, query: String): List<EntryTrackSearch>
    suspend fun unregister(entryId: Long, trackerId: Long)
}

@Inject
class MangaEntryTracking(
    private val getTracks: GetMangaTracks,
    private val getManga: GetManga,
    private val refreshTracks: RefreshMangaTracks,
    private val sourceManager: MangaSourceManager,
    private val deleteTrack: DeleteMangaTrack,
) : EntryTracking {

    override fun subscribeTracks(entryId: Long) =
        getTracks.subscribe(entryId).map { tracks -> tracks.map(EntryTrack::Manga) }

    override suspend fun refreshTracks(entryId: Long) = refreshTracks.await(entryId)

    override fun supports(tracker: Tracker) = tracker is MangaTracker

    override fun accepts(tracker: Tracker, sourceId: Long) =
        (tracker as? EnhancedMangaTracker)?.accept(sourceManager.getOrStub(sourceId)) ?: true

    override fun isEnhanced(tracker: Tracker) = tracker is EnhancedMangaTracker

    override suspend fun registerEnhanced(tracker: Tracker, entryId: Long) {
        val manga = getManga.await(entryId) ?: return
        val matchResult = (tracker as EnhancedMangaTracker).match(manga) ?: throw Exception()
        tracker.mangaService.register(matchResult, entryId)
    }

    override suspend fun search(tracker: Tracker, query: String) =
        tracker.mangaService.searchManga(query).map(EntryTrackSearch::Manga)

    override suspend fun unregister(entryId: Long, trackerId: Long) = deleteTrack.await(entryId, trackerId)
}

@Inject
class AnimeEntryTracking(
    private val getTracks: GetAnimeTracks,
    private val getAnime: GetAnime,
    private val refreshTracks: RefreshAnimeTracks,
    private val sourceManager: AnimeSourceManager,
    private val deleteTrack: DeleteAnimeTrack,
) : EntryTracking {

    override fun subscribeTracks(entryId: Long) =
        getTracks.subscribe(entryId).map { tracks -> tracks.map(EntryTrack::Anime) }

    override suspend fun refreshTracks(entryId: Long) = refreshTracks.await(entryId)

    override fun supports(tracker: Tracker) = tracker is AnimeTracker

    override fun accepts(tracker: Tracker, sourceId: Long) =
        (tracker as? EnhancedAnimeTracker)?.accept(sourceManager.getOrStub(sourceId)) ?: true

    override fun isEnhanced(tracker: Tracker) = tracker is EnhancedAnimeTracker

    override suspend fun registerEnhanced(tracker: Tracker, entryId: Long) {
        val anime = getAnime.await(entryId) ?: return
        val matchResult = (tracker as EnhancedAnimeTracker).match(anime) ?: throw Exception()
        tracker.animeService.register(matchResult, entryId)
    }

    override suspend fun search(tracker: Tracker, query: String) =
        tracker.animeService.searchAnime(query).map(EntryTrackSearch::Anime)

    override suspend fun unregister(entryId: Long, trackerId: Long) = deleteTrack.await(entryId, trackerId)
}
