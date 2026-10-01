package eu.kanade.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.manga.interactor.ToggleMangaIncognito
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import mihon.data.repository.anime.AnimeExtensionRepoRepositoryImpl
import mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepoCount
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepoCount
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.service.ExtensionRepoService
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.source.anime.AnimeSourceRepositoryImpl
import tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaStubSourceRepositoryImpl
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.source.anime.interactor.GetAnimeSourcesWithNonLibraryAnime
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.interactor.GetMangaSourcesWithNonLibraryManga
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager

@BindingContainer
object SourceBindings {

    @Provides
    fun provideGetAnimeExtensionsByType(
        preferences: SourcePreferences,
        extensionManager: AnimeExtensionManager,
    ): GetAnimeExtensionsByType =
        GetAnimeExtensionsByType(preferences = preferences, extensionManager = extensionManager)

    @Provides
    fun provideGetAnimeExtensionSources(preferences: SourcePreferences): GetAnimeExtensionSources =
        GetAnimeExtensionSources(preferences = preferences)

    @Provides
    fun provideGetAnimeExtensionLanguages(
        preferences: SourcePreferences,
        extensionManager: AnimeExtensionManager,
    ): GetAnimeExtensionLanguages =
        GetAnimeExtensionLanguages(preferences = preferences, extensionManager = extensionManager)

    @Provides
    fun provideGetMangaExtensionsByType(
        preferences: SourcePreferences,
        extensionManager: MangaExtensionManager,
    ): GetMangaExtensionsByType =
        GetMangaExtensionsByType(preferences = preferences, extensionManager = extensionManager)

    @Provides
    fun provideGetExtensionSources(preferences: SourcePreferences): GetExtensionSources =
        GetExtensionSources(preferences = preferences)

    @Provides
    fun provideGetMangaExtensionLanguages(
        preferences: SourcePreferences,
        extensionManager: MangaExtensionManager,
    ): GetMangaExtensionLanguages =
        GetMangaExtensionLanguages(preferences = preferences, extensionManager = extensionManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeSourceRepository(
        sourceManager: AnimeSourceManager,
        handler: AnimeDatabaseHandler,
        stubSourceRepository: AnimeStubSourceRepository,
    ): AnimeSourceRepository =
        AnimeSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = handler,
            stubSourceRepository = stubSourceRepository,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeStubSourceRepository(handler: AnimeDatabaseHandler): AnimeStubSourceRepository =
        AnimeStubSourceRepositoryImpl(handler = handler)

    @Provides
    fun provideGetEnabledAnimeSources(
        repository: AnimeSourceRepository,
        preferences: SourcePreferences,
    ): GetEnabledAnimeSources =
        GetEnabledAnimeSources(repository = repository, preferences = preferences)

    @Provides
    fun provideGetLanguagesWithAnimeSources(
        repository: AnimeSourceRepository,
        preferences: SourcePreferences,
    ): GetLanguagesWithAnimeSources =
        GetLanguagesWithAnimeSources(repository = repository, preferences = preferences)

    @Provides
    fun provideGetRemoteAnime(repository: AnimeSourceRepository): GetRemoteAnime =
        GetRemoteAnime(repository = repository)

    @Provides
    fun provideGetAnimeSourcesWithFavoriteCount(
        repository: AnimeSourceRepository,
        preferences: SourcePreferences,
    ): GetAnimeSourcesWithFavoriteCount =
        GetAnimeSourcesWithFavoriteCount(repository = repository, preferences = preferences)

    @Provides
    fun provideGetAnimeSourcesWithNonLibraryAnime(repository: AnimeRepository): GetAnimeSourcesWithNonLibraryAnime =
        GetAnimeSourcesWithNonLibraryAnime(repository = repository)

    @Provides
    fun provideToggleAnimeSource(preferences: SourcePreferences): ToggleAnimeSource =
        ToggleAnimeSource(preferences = preferences)

    @Provides
    fun provideToggleAnimeSourcePin(preferences: SourcePreferences): ToggleAnimeSourcePin =
        ToggleAnimeSourcePin(preferences = preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaSourceRepository(
        sourceManager: MangaSourceManager,
        handler: MangaDatabaseHandler,
        stubSourceRepository: MangaStubSourceRepository,
    ): MangaSourceRepository =
        MangaSourceRepositoryImpl(
            sourceManager = sourceManager,
            handler = handler,
            stubSourceRepository = stubSourceRepository,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaStubSourceRepository(handler: MangaDatabaseHandler): MangaStubSourceRepository =
        MangaStubSourceRepositoryImpl(handler = handler)

    @Provides
    fun provideGetEnabledMangaSources(
        repository: MangaSourceRepository,
        preferences: SourcePreferences,
    ): GetEnabledMangaSources =
        GetEnabledMangaSources(repository = repository, preferences = preferences)

    @Provides
    fun provideGetLanguagesWithMangaSources(
        repository: MangaSourceRepository,
        preferences: SourcePreferences,
    ): GetLanguagesWithMangaSources =
        GetLanguagesWithMangaSources(repository = repository, preferences = preferences)

    @Provides
    fun provideGetRemoteManga(repository: MangaSourceRepository): GetRemoteManga =
        GetRemoteManga(repository = repository)

    @Provides
    fun provideGetMangaSourcesWithFavoriteCount(
        repository: MangaSourceRepository,
        preferences: SourcePreferences,
    ): GetMangaSourcesWithFavoriteCount =
        GetMangaSourcesWithFavoriteCount(repository = repository, preferences = preferences)

    @Provides
    fun provideGetMangaSourcesWithNonLibraryManga(
        repository: MangaSourceRepository,
    ): GetMangaSourcesWithNonLibraryManga =
        GetMangaSourcesWithNonLibraryManga(repository = repository)

    @Provides
    fun provideSetMigrateSorting(preferences: SourcePreferences): SetMigrateSorting =
        SetMigrateSorting(preferences = preferences)

    @Provides
    fun provideToggleLanguage(preferences: SourcePreferences): ToggleLanguage =
        ToggleLanguage(preferences = preferences)

    @Provides
    fun provideToggleMangaSource(preferences: SourcePreferences): ToggleMangaSource =
        ToggleMangaSource(preferences = preferences)

    @Provides
    fun provideToggleMangaSourcePin(preferences: SourcePreferences): ToggleMangaSourcePin =
        ToggleMangaSourcePin(preferences = preferences)

    @Provides
    fun provideTrustAnimeExtension(
        animeExtensionRepoRepository: AnimeExtensionRepoRepository,
        preferences: SourcePreferences,
    ): TrustAnimeExtension =
        TrustAnimeExtension(
            animeExtensionRepoRepository = animeExtensionRepoRepository,
            preferences = preferences,
        )

    @Provides
    fun provideTrustMangaExtension(
        mangaExtensionRepoRepository: MangaExtensionRepoRepository,
        preferences: SourcePreferences,
    ): TrustMangaExtension =
        TrustMangaExtension(
            mangaExtensionRepoRepository = mangaExtensionRepoRepository,
            preferences = preferences,
        )

    @Provides
    fun provideExtensionRepoService(networkHelper: NetworkHelper, json: Json): ExtensionRepoService =
        ExtensionRepoService(networkHelper = networkHelper, json = json)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeExtensionRepoRepository(handler: AnimeDatabaseHandler): AnimeExtensionRepoRepository =
        AnimeExtensionRepoRepositoryImpl(handler = handler)

    @Provides
    fun provideGetAnimeExtensionRepo(repository: AnimeExtensionRepoRepository): GetAnimeExtensionRepo =
        GetAnimeExtensionRepo(repository = repository)

    @Provides
    fun provideGetAnimeExtensionRepoCount(repository: AnimeExtensionRepoRepository): GetAnimeExtensionRepoCount =
        GetAnimeExtensionRepoCount(repository = repository)

    @Provides
    fun provideCreateAnimeExtensionRepo(
        repository: AnimeExtensionRepoRepository,
        service: ExtensionRepoService,
    ): CreateAnimeExtensionRepo =
        CreateAnimeExtensionRepo(repository = repository, service = service)

    @Provides
    fun provideDeleteAnimeExtensionRepo(repository: AnimeExtensionRepoRepository): DeleteAnimeExtensionRepo =
        DeleteAnimeExtensionRepo(repository = repository)

    @Provides
    fun provideReplaceAnimeExtensionRepo(repository: AnimeExtensionRepoRepository): ReplaceAnimeExtensionRepo =
        ReplaceAnimeExtensionRepo(repository = repository)

    @Provides
    fun provideUpdateAnimeExtensionRepo(
        repository: AnimeExtensionRepoRepository,
        service: ExtensionRepoService,
    ): UpdateAnimeExtensionRepo =
        UpdateAnimeExtensionRepo(repository = repository, service = service)

    @Provides
    fun provideToggleAnimeIncognito(preferences: SourcePreferences): ToggleAnimeIncognito =
        ToggleAnimeIncognito(preferences = preferences)

    @Provides
    fun provideGetAnimeIncognitoState(
        basePreferences: BasePreferences,
        sourcePreferences: SourcePreferences,
        extensionManager: AnimeExtensionManager,
    ): GetAnimeIncognitoState =
        GetAnimeIncognitoState(
            basePreferences = basePreferences,
            sourcePreferences = sourcePreferences,
            extensionManager = extensionManager,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaExtensionRepoRepository(handler: MangaDatabaseHandler): MangaExtensionRepoRepository =
        MangaExtensionRepoRepositoryImpl(handler = handler)

    @Provides
    fun provideGetMangaExtensionRepo(repository: MangaExtensionRepoRepository): GetMangaExtensionRepo =
        GetMangaExtensionRepo(repository = repository)

    @Provides
    fun provideGetMangaExtensionRepoCount(repository: MangaExtensionRepoRepository): GetMangaExtensionRepoCount =
        GetMangaExtensionRepoCount(repository = repository)

    @Provides
    fun provideCreateMangaExtensionRepo(
        repository: MangaExtensionRepoRepository,
        service: ExtensionRepoService,
    ): CreateMangaExtensionRepo =
        CreateMangaExtensionRepo(repository = repository, service = service)

    @Provides
    fun provideDeleteMangaExtensionRepo(repository: MangaExtensionRepoRepository): DeleteMangaExtensionRepo =
        DeleteMangaExtensionRepo(repository = repository)

    @Provides
    fun provideReplaceMangaExtensionRepo(repository: MangaExtensionRepoRepository): ReplaceMangaExtensionRepo =
        ReplaceMangaExtensionRepo(repository = repository)

    @Provides
    fun provideUpdateMangaExtensionRepo(
        repository: MangaExtensionRepoRepository,
        service: ExtensionRepoService,
    ): UpdateMangaExtensionRepo =
        UpdateMangaExtensionRepo(repository = repository, service = service)

    @Provides
    fun provideToggleMangaIncognito(preferences: SourcePreferences): ToggleMangaIncognito =
        ToggleMangaIncognito(preferences = preferences)

    @Provides
    fun provideGetMangaIncognitoState(
        basePreferences: BasePreferences,
        sourcePreferences: SourcePreferences,
        extensionManager: MangaExtensionManager,
    ): GetMangaIncognitoState =
        GetMangaIncognitoState(
            basePreferences = basePreferences,
            sourcePreferences = sourcePreferences,
            extensionManager = extensionManager,
        )

    @Provides
    fun provideUpdateMangaFromRemote(
        sourceManager: MangaSourceManager,
        chapterRepository: ChapterRepository,
        mangaRepository: MangaRepository,
        syncChaptersWithSource: SyncChaptersWithSource,
        coverCache: MangaCoverCache,
    ): UpdateMangaFromRemote =
        UpdateMangaFromRemote(
            sourceManager = sourceManager,
            chapterRepository = chapterRepository,
            mangaRepository = mangaRepository,
            syncChaptersWithSource = syncChaptersWithSource,
            coverCache = coverCache,
        )
}
