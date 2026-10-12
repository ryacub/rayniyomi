package eu.kanade.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload
import eu.kanade.domain.download.manga.interactor.DeleteChapterDownload
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.PopulateFillerMarks
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.filler.AnimeFillerSource
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl
import tachiyomi.data.history.manga.MangaHistoryRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl
import tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.history.manga.interactor.RemoveMangaHistory
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.items.season.interactor.ShouldUpdateDbSeason
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository

@BindingContainer
object ItemBindings {

    @Provides
    fun provideGetAnimeSeasonsByParentId(animeRepository: AnimeRepository): GetAnimeSeasonsByParentId =
        GetAnimeSeasonsByParentId(animeRepository = animeRepository)

    @Provides
    fun provideGetNextEpisodes(
        getEpisodesByAnimeId: GetEpisodesByAnimeId,
        getAnime: GetAnime,
        historyRepository: AnimeHistoryRepository,
    ): GetNextEpisodes =
        GetNextEpisodes(
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            getAnime = getAnime,
            historyRepository = historyRepository,
        )

    @Provides
    fun provideSetAnimeDefaultEpisodeFlags(
        libraryPreferences: LibraryPreferences,
        setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
        getFavorites: GetAnimeFavorites,
    ): SetAnimeDefaultEpisodeFlags =
        SetAnimeDefaultEpisodeFlags(
            libraryPreferences = libraryPreferences,
            setAnimeEpisodeFlags = setAnimeEpisodeFlags,
            getFavorites = getFavorites,
        )

    @Provides
    fun provideSetAnimeDefaultSeasonFlags(
        libraryPreferences: LibraryPreferences,
        setAnimeSeasonFlags: SetAnimeSeasonFlags,
        getAnimeFavorites: GetAnimeFavorites,
    ): SetAnimeDefaultSeasonFlags =
        SetAnimeDefaultSeasonFlags(
            libraryPreferences = libraryPreferences,
            setAnimeSeasonFlags = setAnimeSeasonFlags,
            getAnimeFavorites = getAnimeFavorites,
        )

    @Provides
    fun provideShouldUpdateDbSeason(): ShouldUpdateDbSeason =
        ShouldUpdateDbSeason()

    @Provides
    fun provideGetNextChapters(
        getChaptersByMangaId: GetChaptersByMangaId,
        getManga: GetManga,
        historyRepository: MangaHistoryRepository,
    ): GetNextChapters =
        GetNextChapters(
            getChaptersByMangaId = getChaptersByMangaId,
            getManga = getManga,
            historyRepository = historyRepository,
        )

    @Provides
    fun provideSetMangaDefaultChapterFlags(
        libraryPreferences: LibraryPreferences,
        setMangaChapterFlags: SetMangaChapterFlags,
        getFavorites: GetMangaFavorites,
    ): SetMangaDefaultChapterFlags =
        SetMangaDefaultChapterFlags(
            libraryPreferences = libraryPreferences,
            setMangaChapterFlags = setMangaChapterFlags,
            getFavorites = getFavorites,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideEpisodeRepository(handler: AnimeDatabaseHandler): EpisodeRepository =
        EpisodeRepositoryImpl(handler = handler)

    @Provides
    fun provideGetEpisode(episodeRepository: EpisodeRepository): GetEpisode =
        GetEpisode(episodeRepository = episodeRepository)

    @Provides
    fun provideGetEpisodesByAnimeId(episodeRepository: EpisodeRepository): GetEpisodesByAnimeId =
        GetEpisodesByAnimeId(episodeRepository = episodeRepository)

    @Provides
    fun provideGetEpisodeByUrlAndAnimeId(episodeRepository: EpisodeRepository): GetEpisodeByUrlAndAnimeId =
        GetEpisodeByUrlAndAnimeId(episodeRepository = episodeRepository)

    @Provides
    fun provideUpdateEpisode(episodeRepository: EpisodeRepository): UpdateEpisode =
        UpdateEpisode(episodeRepository = episodeRepository)

    @Provides
    fun providePopulateFillerMarks(
        source: AnimeFillerSource,
        updateEpisode: UpdateEpisode,
        libraryPreferences: LibraryPreferences,
    ): PopulateFillerMarks =
        PopulateFillerMarks(
            source = source,
            updateEpisode = updateEpisode,
            libraryPreferences = libraryPreferences,
        )

    @Provides
    fun provideSetSeenStatus(
        downloadPreferences: DownloadPreferences,
        deleteDownload: DeleteEpisodeDownload,
        animeRepository: AnimeRepository,
        episodeRepository: EpisodeRepository,
    ): SetSeenStatus =
        SetSeenStatus(
            downloadPreferences = downloadPreferences,
            deleteDownload = deleteDownload,
            animeRepository = animeRepository,
            episodeRepository = episodeRepository,
        )

    @Provides
    fun provideShouldUpdateDbEpisode(): ShouldUpdateDbEpisode =
        ShouldUpdateDbEpisode()

    @Provides
    fun provideSyncEpisodesWithSource(
        downloadManager: AnimeDownloadManager,
        downloadProvider: AnimeDownloadProvider,
        episodeRepository: EpisodeRepository,
        shouldUpdateDbEpisode: ShouldUpdateDbEpisode,
        updateAnime: UpdateAnime,
        getEpisodesByAnimeId: GetEpisodesByAnimeId,
        libraryPreferences: LibraryPreferences,
    ): SyncEpisodesWithSource =
        SyncEpisodesWithSource(
            downloadManager = downloadManager,
            downloadProvider = downloadProvider,
            episodeRepository = episodeRepository,
            shouldUpdateDbEpisode = shouldUpdateDbEpisode,
            updateAnime = updateAnime,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            libraryPreferences = libraryPreferences,
        )

    @Provides
    fun provideFilterEpisodesForDownload(
        getEpisodesByAnimeId: GetEpisodesByAnimeId,
        downloadPreferences: DownloadPreferences,
        getCategories: GetAnimeCategories,
    ): FilterEpisodesForDownload =
        FilterEpisodesForDownload(
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            downloadPreferences = downloadPreferences,
            getCategories = getCategories,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideChapterRepository(handler: MangaDatabaseHandler): ChapterRepository =
        ChapterRepositoryImpl(handler = handler)

    @Provides
    fun provideGetChapter(chapterRepository: ChapterRepository): GetChapter =
        GetChapter(chapterRepository = chapterRepository)

    @Provides
    fun provideGetChaptersByMangaId(chapterRepository: ChapterRepository): GetChaptersByMangaId =
        GetChaptersByMangaId(chapterRepository = chapterRepository)

    @Provides
    fun provideGetChapterByUrlAndMangaId(chapterRepository: ChapterRepository): GetChapterByUrlAndMangaId =
        GetChapterByUrlAndMangaId(chapterRepository = chapterRepository)

    @Provides
    fun provideUpdateChapter(chapterRepository: ChapterRepository): UpdateChapter =
        UpdateChapter(chapterRepository = chapterRepository)

    @Provides
    fun provideSetReadStatus(
        downloadPreferences: DownloadPreferences,
        deleteDownload: DeleteChapterDownload,
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
    ): SetReadStatus =
        SetReadStatus(
            downloadPreferences = downloadPreferences,
            deleteDownload = deleteDownload,
            mangaRepository = mangaRepository,
            chapterRepository = chapterRepository,
        )

    @Provides
    fun provideShouldUpdateDbChapter(): ShouldUpdateDbChapter =
        ShouldUpdateDbChapter()

    @Provides
    fun provideSyncChaptersWithSource(
        downloadManager: MangaDownloadManager,
        downloadProvider: MangaDownloadProvider,
        chapterRepository: ChapterRepository,
        shouldUpdateDbChapter: ShouldUpdateDbChapter,
        updateManga: UpdateManga,
        getChaptersByMangaId: GetChaptersByMangaId,
        getExcludedScanlators: GetExcludedScanlators,
        libraryPreferences: LibraryPreferences,
    ): SyncChaptersWithSource =
        SyncChaptersWithSource(
            downloadManager = downloadManager,
            downloadProvider = downloadProvider,
            chapterRepository = chapterRepository,
            shouldUpdateDbChapter = shouldUpdateDbChapter,
            updateManga = updateManga,
            getChaptersByMangaId = getChaptersByMangaId,
            getExcludedScanlators = getExcludedScanlators,
            libraryPreferences = libraryPreferences,
        )

    @Provides
    fun provideGetAvailableScanlators(repository: ChapterRepository): GetAvailableScanlators =
        GetAvailableScanlators(repository = repository)

    @Provides
    fun provideFilterChaptersForDownload(
        getChaptersByMangaId: GetChaptersByMangaId,
        downloadPreferences: DownloadPreferences,
        getCategories: GetMangaCategories,
    ): FilterChaptersForDownload =
        FilterChaptersForDownload(
            getChaptersByMangaId = getChaptersByMangaId,
            downloadPreferences = downloadPreferences,
            getCategories = getCategories,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeHistoryRepository(handler: AnimeDatabaseHandler): AnimeHistoryRepository =
        AnimeHistoryRepositoryImpl(handler = handler)

    @Provides
    fun provideGetAnimeHistory(repository: AnimeHistoryRepository): GetAnimeHistory =
        GetAnimeHistory(repository = repository)

    @Provides
    fun provideUpsertAnimeHistory(historyRepository: AnimeHistoryRepository): UpsertAnimeHistory =
        UpsertAnimeHistory(historyRepository = historyRepository)

    @Provides
    fun provideRemoveAnimeHistory(repository: AnimeHistoryRepository): RemoveAnimeHistory =
        RemoveAnimeHistory(repository = repository)

    @Provides
    fun provideDeleteEpisodeDownload(
        sourceManager: AnimeSourceManager,
        downloadManager: AnimeDownloadManager,
    ): DeleteEpisodeDownload =
        DeleteEpisodeDownload(sourceManager = sourceManager, downloadManager = downloadManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaHistoryRepository(handler: MangaDatabaseHandler): MangaHistoryRepository =
        MangaHistoryRepositoryImpl(handler = handler)

    @Provides
    fun provideGetMangaHistory(repository: MangaHistoryRepository): GetMangaHistory =
        GetMangaHistory(repository = repository)

    @Provides
    fun provideUpsertMangaHistory(historyRepository: MangaHistoryRepository): UpsertMangaHistory =
        UpsertMangaHistory(historyRepository = historyRepository)

    @Provides
    fun provideRemoveMangaHistory(repository: MangaHistoryRepository): RemoveMangaHistory =
        RemoveMangaHistory(repository = repository)

    @Provides
    fun provideGetTotalReadDuration(repository: MangaHistoryRepository): GetTotalReadDuration =
        GetTotalReadDuration(repository = repository)

    @Provides
    fun provideDeleteChapterDownload(
        sourceManager: MangaSourceManager,
        downloadManager: MangaDownloadManager,
    ): DeleteChapterDownload =
        DeleteChapterDownload(sourceManager = sourceManager, downloadManager = downloadManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeUpdatesRepository(databaseHandler: AnimeDatabaseHandler): AnimeUpdatesRepository =
        AnimeUpdatesRepositoryImpl(databaseHandler = databaseHandler)

    @Provides
    fun provideGetAnimeUpdates(repository: AnimeUpdatesRepository): GetAnimeUpdates =
        GetAnimeUpdates(repository = repository)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaUpdatesRepository(databaseHandler: MangaDatabaseHandler): MangaUpdatesRepository =
        MangaUpdatesRepositoryImpl(databaseHandler = databaseHandler)

    @Provides
    fun provideGetMangaUpdates(repository: MangaUpdatesRepository): GetMangaUpdates =
        GetMangaUpdates(repository = repository)
}
