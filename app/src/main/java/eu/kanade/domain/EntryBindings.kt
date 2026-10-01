package eu.kanade.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.MergeLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.MergeLibraryManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.entries.manga.interactor.ScanLibraryDuplicates
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.items.season.interactor.ShouldUpdateDbSeason
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

@BindingContainer
object EntryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeRepository(handler: AnimeDatabaseHandler): AnimeRepository =
        AnimeRepositoryImpl(handler = handler)

    @Provides
    fun provideGetDuplicateLibraryAnime(
        animeRepository: AnimeRepository,
        getAnimeTracks: GetAnimeTracks,
    ): GetDuplicateLibraryAnime =
        GetDuplicateLibraryAnime(animeRepository = animeRepository, getAnimeTracks = getAnimeTracks)

    @Provides
    fun provideMergeLibraryAnime(animeRepository: AnimeRepository): MergeLibraryAnime =
        MergeLibraryAnime(animeRepository = animeRepository)

    @Provides
    fun provideGetAnimeFavorites(animeRepository: AnimeRepository): GetAnimeFavorites =
        GetAnimeFavorites(animeRepository = animeRepository)

    @Provides
    fun provideGetLibraryAnime(animeRepository: AnimeRepository): GetLibraryAnime =
        GetLibraryAnime(animeRepository = animeRepository)

    @Provides
    fun provideGetAnimeWithEpisodesAndSeasons(
        animeRepository: AnimeRepository,
        episodeRepository: EpisodeRepository,
    ): GetAnimeWithEpisodesAndSeasons =
        GetAnimeWithEpisodesAndSeasons(
            animeRepository = animeRepository,
            episodeRepository = episodeRepository,
        )

    @Provides
    fun provideGetAnimeByUrlAndSourceId(animeRepository: AnimeRepository): GetAnimeByUrlAndSourceId =
        GetAnimeByUrlAndSourceId(animeRepository = animeRepository)

    @Provides
    fun provideGetAnime(animeRepository: AnimeRepository): GetAnime =
        GetAnime(animeRepository = animeRepository)

    @Provides
    fun provideGetUpcomingAnime(animeRepository: AnimeRepository): GetUpcomingAnime =
        GetUpcomingAnime(animeRepository = animeRepository)

    @Provides
    fun provideResetAnimeViewerFlags(animeRepository: AnimeRepository): ResetAnimeViewerFlags =
        ResetAnimeViewerFlags(animeRepository = animeRepository)

    @Provides
    fun provideSetAnimeEpisodeFlags(animeRepository: AnimeRepository): SetAnimeEpisodeFlags =
        SetAnimeEpisodeFlags(animeRepository = animeRepository)

    @Provides
    fun provideSetAnimeSeasonFlags(animeRepository: AnimeRepository): SetAnimeSeasonFlags =
        SetAnimeSeasonFlags(animeRepository = animeRepository)

    @Provides
    fun provideAnimeFetchInterval(getEpisodesByAnimeId: GetEpisodesByAnimeId): AnimeFetchInterval =
        AnimeFetchInterval(getEpisodesByAnimeId = getEpisodesByAnimeId)

    @Provides
    fun provideSetAnimeViewerFlags(animeRepository: AnimeRepository): SetAnimeViewerFlags =
        SetAnimeViewerFlags(animeRepository = animeRepository)

    @Provides
    fun provideNetworkToLocalAnime(
        animeRepository: AnimeRepository,
        sourceManager: AnimeSourceManager,
    ): NetworkToLocalAnime =
        NetworkToLocalAnime(animeRepository = animeRepository, sourceManager = sourceManager)

    @Provides
    fun provideUpdateAnime(
        animeRepository: AnimeRepository,
        animeFetchInterval: AnimeFetchInterval,
    ): UpdateAnime =
        UpdateAnime(animeRepository = animeRepository, animeFetchInterval = animeFetchInterval)

    @Provides
    fun provideSyncSeasonsWithSource(
        updateAnime: UpdateAnime,
        animeRepository: AnimeRepository,
        networkToLocalAnime: NetworkToLocalAnime,
        shouldUpdateDbSeason: ShouldUpdateDbSeason,
        getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId,
    ): SyncSeasonsWithSource =
        SyncSeasonsWithSource(
            updateAnime = updateAnime,
            animeRepository = animeRepository,
            networkToLocalAnime = networkToLocalAnime,
            shouldUpdateDbSeason = shouldUpdateDbSeason,
            getAnimeSeasonsByParentId = getAnimeSeasonsByParentId,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaRepository(handler: MangaDatabaseHandler): MangaRepository =
        MangaRepositoryImpl(handler = handler)

    @Provides
    fun provideGetDuplicateLibraryManga(
        mangaRepository: MangaRepository,
        getMangaTracks: GetMangaTracks,
    ): GetDuplicateLibraryManga =
        GetDuplicateLibraryManga(mangaRepository = mangaRepository, getMangaTracks = getMangaTracks)

    @Provides
    fun provideMergeLibraryManga(mangaRepository: MangaRepository): MergeLibraryManga =
        MergeLibraryManga(mangaRepository = mangaRepository)

    @Provides
    fun provideScanLibraryDuplicates(
        getLibraryManga: GetLibraryManga,
        getDuplicateLibraryManga: GetDuplicateLibraryManga,
    ): ScanLibraryDuplicates =
        ScanLibraryDuplicates(
            getLibraryManga = getLibraryManga,
            getDuplicateLibraryManga = getDuplicateLibraryManga,
        )

    @Provides
    fun provideGetMangaFavorites(mangaRepository: MangaRepository): GetMangaFavorites =
        GetMangaFavorites(mangaRepository = mangaRepository)

    @Provides
    fun provideGetLibraryManga(mangaRepository: MangaRepository): GetLibraryManga =
        GetLibraryManga(mangaRepository = mangaRepository)

    @Provides
    fun provideGetMangaWithChapters(
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
    ): GetMangaWithChapters =
        GetMangaWithChapters(mangaRepository = mangaRepository, chapterRepository = chapterRepository)

    @Provides
    fun provideGetMangaByUrlAndSourceId(mangaRepository: MangaRepository): GetMangaByUrlAndSourceId =
        GetMangaByUrlAndSourceId(mangaRepository = mangaRepository)

    @Provides
    fun provideGetManga(mangaRepository: MangaRepository): GetManga =
        GetManga(mangaRepository = mangaRepository)

    @Provides
    fun provideGetUpcomingManga(mangaRepository: MangaRepository): GetUpcomingManga =
        GetUpcomingManga(mangaRepository = mangaRepository)

    @Provides
    fun provideResetMangaViewerFlags(mangaRepository: MangaRepository): ResetMangaViewerFlags =
        ResetMangaViewerFlags(mangaRepository = mangaRepository)

    @Provides
    fun provideSetMangaChapterFlags(mangaRepository: MangaRepository): SetMangaChapterFlags =
        SetMangaChapterFlags(mangaRepository = mangaRepository)

    @Provides
    fun provideMangaFetchInterval(getChaptersByMangaId: GetChaptersByMangaId): MangaFetchInterval =
        MangaFetchInterval(getChaptersByMangaId = getChaptersByMangaId)

    @Provides
    fun provideSetMangaViewerFlags(mangaRepository: MangaRepository): SetMangaViewerFlags =
        SetMangaViewerFlags(mangaRepository = mangaRepository)

    @Provides
    fun provideNetworkToLocalManga(mangaRepository: MangaRepository): NetworkToLocalManga =
        NetworkToLocalManga(mangaRepository = mangaRepository)

    @Provides
    fun provideUpdateManga(
        mangaRepository: MangaRepository,
        mangaFetchInterval: MangaFetchInterval,
    ): UpdateManga =
        UpdateManga(mangaRepository = mangaRepository, mangaFetchInterval = mangaFetchInterval)

    @Provides
    fun provideGetExcludedScanlators(handler: MangaDatabaseHandler): GetExcludedScanlators =
        GetExcludedScanlators(handler = handler)

    @Provides
    fun provideSetExcludedScanlators(handler: MangaDatabaseHandler): SetExcludedScanlators =
        SetExcludedScanlators(handler = handler)
}
