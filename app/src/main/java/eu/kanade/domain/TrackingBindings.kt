package eu.kanade.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.interactor.TrackSyncConflictResolver
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.service.TrackerSyncCoordinator
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.track.anime.AnimeTrackRepositoryImpl
import tachiyomi.data.track.manga.MangaTrackRepositoryImpl
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository

@BindingContainer
object TrackingBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeTrackRepository(handler: AnimeDatabaseHandler): AnimeTrackRepository =
        AnimeTrackRepositoryImpl(handler = handler)

    @Provides
    fun provideTrackEpisode(
        getTracks: GetAnimeTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertAnimeTrack,
        delayedTrackingStore: DelayedAnimeTrackingStore,
    ): TrackEpisode =
        TrackEpisode(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = insertTrack,
            delayedTrackingStore = delayedTrackingStore,
        )

    @Provides
    fun provideAddAnimeTracks(
        insertTrack: InsertAnimeTrack,
        syncChapterProgressWithTrack: SyncEpisodeProgressWithTrack,
        getEpisodesByAnimeId: GetEpisodesByAnimeId,
        trackerManager: TrackerManager,
    ): AddAnimeTracks =
        AddAnimeTracks(
            insertTrack = insertTrack,
            syncChapterProgressWithTrack = syncChapterProgressWithTrack,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            trackerManager = trackerManager,
        )

    @Provides
    fun provideRefreshAnimeTracks(
        getTracks: GetAnimeTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertAnimeTrack,
        syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack,
    ): RefreshAnimeTracks =
        RefreshAnimeTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = insertTrack,
            syncEpisodeProgressWithTrack = syncEpisodeProgressWithTrack,
        )

    @Provides
    fun provideDeleteAnimeTrack(trackRepository: AnimeTrackRepository): DeleteAnimeTrack =
        DeleteAnimeTrack(trackRepository = trackRepository)

    @Provides
    fun provideGetTracksPerAnime(trackRepository: AnimeTrackRepository): GetTracksPerAnime =
        GetTracksPerAnime(trackRepository = trackRepository)

    @Provides
    fun provideGetAnimeTracks(animetrackRepository: AnimeTrackRepository): GetAnimeTracks =
        GetAnimeTracks(animetrackRepository = animetrackRepository)

    @Provides
    fun provideInsertAnimeTrack(animetrackRepository: AnimeTrackRepository): InsertAnimeTrack =
        InsertAnimeTrack(animetrackRepository = animetrackRepository)

    @Provides
    fun provideSyncEpisodeProgressWithTrack(
        updateEpisode: UpdateEpisode,
        insertTrack: InsertAnimeTrack,
        getEpisodesByAnimeId: GetEpisodesByAnimeId,
    ): SyncEpisodeProgressWithTrack =
        SyncEpisodeProgressWithTrack(
            updateEpisode = updateEpisode,
            insertTrack = insertTrack,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaTrackRepository(handler: MangaDatabaseHandler): MangaTrackRepository =
        MangaTrackRepositoryImpl(handler = handler)

    @Provides
    fun provideTrackChapter(
        getTracks: GetMangaTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertMangaTrack,
        delayedTrackingStore: DelayedMangaTrackingStore,
    ): TrackChapter =
        TrackChapter(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = insertTrack,
            delayedTrackingStore = delayedTrackingStore,
        )

    @Provides
    fun provideAddMangaTracks(
        insertTrack: InsertMangaTrack,
        syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
        getChaptersByMangaId: GetChaptersByMangaId,
        trackerManager: TrackerManager,
    ): AddMangaTracks =
        AddMangaTracks(
            insertTrack = insertTrack,
            syncChapterProgressWithTrack = syncChapterProgressWithTrack,
            getChaptersByMangaId = getChaptersByMangaId,
            trackerManager = trackerManager,
        )

    @Provides
    fun provideRefreshMangaTracks(
        getTracks: GetMangaTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertMangaTrack,
        syncChapterProgressWithTrack: SyncChapterProgressWithTrack,
    ): RefreshMangaTracks =
        RefreshMangaTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = insertTrack,
            syncChapterProgressWithTrack = syncChapterProgressWithTrack,
        )

    @Provides
    fun provideDeleteMangaTrack(trackRepository: MangaTrackRepository): DeleteMangaTrack =
        DeleteMangaTrack(trackRepository = trackRepository)

    @Provides
    fun provideGetTracksPerManga(trackRepository: MangaTrackRepository): GetTracksPerManga =
        GetTracksPerManga(trackRepository = trackRepository)

    @Provides
    fun provideGetMangaTracks(trackRepository: MangaTrackRepository): GetMangaTracks =
        GetMangaTracks(trackRepository = trackRepository)

    @Provides
    fun provideInsertMangaTrack(trackRepository: MangaTrackRepository): InsertMangaTrack =
        InsertMangaTrack(trackRepository = trackRepository)

    @Provides
    fun provideSyncChapterProgressWithTrack(
        updateChapter: UpdateChapter,
        insertTrack: InsertMangaTrack,
        getChaptersByMangaId: GetChaptersByMangaId,
    ): SyncChapterProgressWithTrack =
        SyncChapterProgressWithTrack(
            updateChapter = updateChapter,
            insertTrack = insertTrack,
            getChaptersByMangaId = getChaptersByMangaId,
        )

    @Provides
    fun provideTrackSyncConflictResolver(): TrackSyncConflictResolver =
        TrackSyncConflictResolver()

    @Provides
    fun provideRefreshAllMangaTracks(
        getTracks: GetMangaTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertMangaTrack,
        deleteTrack: DeleteMangaTrack,
        getChaptersByMangaId: GetChaptersByMangaId,
        updateChapter: UpdateChapter,
        conflictResolver: TrackSyncConflictResolver,
    ): RefreshAllMangaTracks =
        RefreshAllMangaTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = insertTrack,
            deleteTrack = deleteTrack,
            getChaptersByMangaId = getChaptersByMangaId,
            updateChapter = updateChapter,
            conflictResolver = conflictResolver,
        )

    @Provides
    fun provideRefreshAllAnimeTracks(
        getTracks: GetAnimeTracks,
        trackerManager: TrackerManager,
        insertTrack: InsertAnimeTrack,
        deleteTrack: DeleteAnimeTrack,
        getEpisodesByAnimeId: GetEpisodesByAnimeId,
        updateEpisode: UpdateEpisode,
        conflictResolver: TrackSyncConflictResolver,
    ): RefreshAllAnimeTracks =
        RefreshAllAnimeTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = insertTrack,
            deleteTrack = deleteTrack,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            updateEpisode = updateEpisode,
            conflictResolver = conflictResolver,
        )

    @Provides
    fun provideTrackerSyncCoordinator(
        trackPreferences: TrackPreferences,
        refreshAllMangaTracks: RefreshAllMangaTracks,
        refreshAllAnimeTracks: RefreshAllAnimeTracks,
    ): TrackerSyncCoordinator =
        TrackerSyncCoordinator(
            trackPreferences = trackPreferences,
            refreshAllMangaTracks = refreshAllMangaTracks,
            refreshAllAnimeTracks = refreshAllAnimeTracks,
        )

    @Provides
    fun provideTrackSelect(
        subtitlePreferences: SubtitlePreferences,
        audioPreferences: AudioPreferences,
    ): TrackSelect =
        TrackSelect(subtitlePreferences = subtitlePreferences, audioPreferences = audioPreferences)
}
