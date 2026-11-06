package eu.kanade.tachiyomi.ui.recovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.recovery.AnimeRecoveryApplierImpl
import tachiyomi.data.recovery.AnimeRecoveryRepositoryImpl
import tachiyomi.data.recovery.MangaRecoveryApplierImpl
import tachiyomi.data.recovery.MangaRecoveryRepositoryImpl
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

class RecoveryBackends(
    private val manga: MangaRecoveryBackend,
    private val anime: AnimeRecoveryBackend,
) {
    fun of(media: RecoveryMedia): RecoveryBackend = when (media) {
        RecoveryMedia.MANGA -> manga
        RecoveryMedia.ANIME -> anime
    }
}

@BindingContainer
object RecoveryModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaRecoveryRepository(handler: MangaDatabaseHandler): MangaRecoveryRepositoryImpl =
        MangaRecoveryRepositoryImpl(handler)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeRecoveryRepository(handler: AnimeDatabaseHandler): AnimeRecoveryRepositoryImpl =
        AnimeRecoveryRepositoryImpl(handler)

    @Provides
    @SingleIn(AppScope::class)
    @Suppress("LongParameterList")
    fun provideMangaRecoveryBackend(
        repository: MangaRecoveryRepositoryImpl,
        handler: MangaDatabaseHandler,
        sourceManager: MangaSourceManager,
        getManga: GetManga,
        getFavorites: GetMangaFavorites,
        getChapters: GetChaptersByMangaId,
        getTracks: GetMangaTracks,
        trackerManager: TrackerManager,
        coverCache: MangaCoverCache,
        sourcePreferences: SourcePreferences,
    ): MangaRecoveryBackend = MangaRecoveryBackend(
        repository = repository,
        applier = MangaRecoveryApplierImpl(handler),
        sourceManager = sourceManager,
        getManga = getManga,
        getFavorites = getFavorites,
        getChapters = getChapters,
        getTracks = getTracks,
        trackerManager = trackerManager,
        coverCache = coverCache,
        sourcePreferences = sourcePreferences,
    )

    @Provides
    @SingleIn(AppScope::class)
    @Suppress("LongParameterList")
    fun provideAnimeRecoveryBackend(
        repository: AnimeRecoveryRepositoryImpl,
        handler: AnimeDatabaseHandler,
        sourceManager: AnimeSourceManager,
        getAnime: GetAnime,
        getFavorites: GetAnimeFavorites,
        getEpisodes: GetEpisodesByAnimeId,
        getTracks: GetAnimeTracks,
        trackerManager: TrackerManager,
        coverCache: AnimeCoverCache,
        sourcePreferences: SourcePreferences,
    ): AnimeRecoveryBackend = AnimeRecoveryBackend(
        repository = repository,
        applier = AnimeRecoveryApplierImpl(handler),
        sourceManager = sourceManager,
        getAnime = getAnime,
        getFavorites = getFavorites,
        getEpisodes = getEpisodes,
        getTracks = getTracks,
        trackerManager = trackerManager,
        coverCache = coverCache,
        sourcePreferences = sourcePreferences,
    )

    @Provides
    fun provideRecoveryBackends(manga: MangaRecoveryBackend, anime: AnimeRecoveryBackend): RecoveryBackends =
        RecoveryBackends(manga, anime)
}
