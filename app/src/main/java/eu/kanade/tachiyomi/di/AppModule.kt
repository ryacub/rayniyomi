package eu.kanade.tachiyomi.di

import android.app.Application
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import data.Chapters
import data.History
import data.Mangas
import dataanime.Animehistory
import dataanime.Animes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.anime.multithread.MultiThreadDownloader
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadStateStore
import eu.kanade.tachiyomi.data.download.anime.strategy.DownloadStrategySelector
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.filler.AnimeFillerListSource
import eu.kanade.tachiyomi.data.filler.AnimeFillerSource
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationEngineFactory
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationRunTelemetry
import eu.kanade.tachiyomi.data.translation.TranslationRunTelemetryFactory
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.feature.novel.LightNovelFeatureGate
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginLauncher
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginReadiness
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginStateManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.source.manga.AndroidMangaSourceManager
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode.Charset
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.AndroidMangaDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem

@Qualifier
annotation class MangaDatabaseDriver

@Qualifier
annotation class AnimeDatabaseDriver

@BindingContainer
object AppModule {

    @Provides
    @SingleIn(AppScope::class)
    @MangaDatabaseDriver
    fun provideMangaDatabaseDriver(application: Application): AndroidSqliteDriver =
        AndroidSqliteDriver(
            schema = Database.Schema,
            context = application,
            name = "tachiyomi.db",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // The debug driver supports the Android Studio database inspector.
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

    @Provides
    @SingleIn(AppScope::class)
    @AnimeDatabaseDriver
    fun provideAnimeDatabaseDriver(application: Application): AndroidSqliteDriver =
        AndroidSqliteDriver(
            schema = AnimeDatabase.Schema,
            context = application,
            name = "tachiyomi.animedb",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // The debug driver supports the Android Studio database inspector.
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(@MangaDatabaseDriver driver: AndroidSqliteDriver): Database =
        Database(
            driver = driver,
            historyAdapter = History.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = MangaUpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(
                memoAdapter = MemoColumnAdapter,
            ),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeDatabase(@AnimeDatabaseDriver driver: AndroidSqliteDriver): AnimeDatabase =
        AnimeDatabase(
            driver = driver,
            animehistoryAdapter = Animehistory.Adapter(
                last_seenAdapter = DateColumnAdapter,
            ),
            animesAdapter = Animes.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaDatabaseHandler(
        database: Database,
        @MangaDatabaseDriver driver: AndroidSqliteDriver,
    ): MangaDatabaseHandler =
        AndroidMangaDatabaseHandler(database, driver)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeDatabaseHandler(
        database: AnimeDatabase,
        @AnimeDatabaseDriver driver: AndroidSqliteDriver,
    ): AnimeDatabaseHandler =
        AndroidAnimeDatabaseHandler(database, driver)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @SingleIn(AppScope::class)
    fun provideXML(): XML =
        XML {
            defaultPolicy {
                ignoreUnknownChildren()
            }
            autoPolymorphic = true
            xmlDeclMode = Charset
            indent = 2
            xmlVersion = XmlVersion.XML10
        }

    @Provides
    @SingleIn(AppScope::class)
    fun provideProtoBuf(): ProtoBuf =
        ProtoBuf

    @Provides
    @SingleIn(AppScope::class)
    fun provideOkHttpClient(networkHelper: NetworkHelper): OkHttpClient =
        networkHelper.client

    @Provides
    @SingleIn(AppScope::class)
    fun provideMultiThreadDownloader(
        client: OkHttpClient,
        stateStore: DownloadStateStore,
        downloadPreferences: DownloadPreferences,
    ): MultiThreadDownloader =
        MultiThreadDownloader(
            client = client,
            stateStore = stateStore,
            maxThreadsProvider = {
                downloadPreferences.multiThreadConnections().get().coerceIn(1, 4)
            },
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslationRunTelemetry(application: Application): TranslationRunTelemetry =
        TranslationRunTelemetryFactory.create(application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLightNovelPluginReadiness(pluginManager: LightNovelPluginManager): LightNovelPluginReadiness =
        pluginManager

    @Provides
    @SingleIn(AppScope::class)
    fun provideChapterCache(application: Application, json: Json): ChapterCache =
        ChapterCache(context = application, json = json)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaCoverCache(application: Application): MangaCoverCache =
        MangaCoverCache(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeCoverCache(application: Application): AnimeCoverCache =
        AnimeCoverCache(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeBackgroundCache(application: Application): AnimeBackgroundCache =
        AnimeBackgroundCache(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideNetworkHelper(application: Application, preferences: NetworkPreferences): NetworkHelper =
        NetworkHelper(context = application, preferences = preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideJavaScriptEngine(application: Application): JavaScriptEngine =
        JavaScriptEngine(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaSourceManager(
        application: Application,
        extensionManager: MangaExtensionManager,
        sourceRepository: MangaStubSourceRepository,
        localMangaSourceFileSystem: LocalMangaSourceFileSystem,
        localMangaCoverManager: LocalMangaCoverManager,
        downloadManagerProvider: Lazy<MangaDownloadManager>,
    ): MangaSourceManager =
        AndroidMangaSourceManager(
            context = application,
            extensionManager = extensionManager,
            sourceRepository = sourceRepository,
            localMangaSourceFileSystem = localMangaSourceFileSystem,
            localMangaCoverManager = localMangaCoverManager,
            downloadManagerProvider = downloadManagerProvider,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeSourceManager(
        application: Application,
        extensionManager: AnimeExtensionManager,
        sourceRepository: AnimeStubSourceRepository,
        localAnimeSourceFileSystem: LocalAnimeSourceFileSystem,
        localAnimeCoverManager: LocalAnimeCoverManager,
        localAnimeBackgroundManager: LocalAnimeBackgroundManager,
        localEpisodeThumbnailManager: LocalEpisodeThumbnailManager,
        localAnimeFetchTypeManager: LocalAnimeFetchTypeManager,
        downloadManagerProvider: Lazy<AnimeDownloadManager>,
    ): AnimeSourceManager =
        AndroidAnimeSourceManager(
            context = application,
            extensionManager = extensionManager,
            sourceRepository = sourceRepository,
            localAnimeSourceFileSystem = localAnimeSourceFileSystem,
            localAnimeCoverManager = localAnimeCoverManager,
            localAnimeBackgroundManager = localAnimeBackgroundManager,
            localEpisodeThumbnailManager = localEpisodeThumbnailManager,
            localAnimeFetchTypeManager = localAnimeFetchTypeManager,
            downloadManagerProvider = downloadManagerProvider,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaExtensionManager(
        application: Application,
        preferences: SourcePreferences,
        trustExtension: TrustMangaExtension,
    ): MangaExtensionManager =
        MangaExtensionManager(
            context = application,
            preferences = preferences,
            trustExtension = trustExtension,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeExtensionManager(
        application: Application,
        preferences: SourcePreferences,
        trustExtension: TrustAnimeExtension,
    ): AnimeExtensionManager =
        AnimeExtensionManager(
            context = application,
            preferences = preferences,
            trustExtension = trustExtension,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaDownloadProvider(application: Application, storageManager: StorageManager): MangaDownloadProvider =
        MangaDownloadProvider(context = application, storageManager = storageManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaDownloadManager(
        application: Application,
        storageManager: StorageManager,
        provider: MangaDownloadProvider,
        cache: MangaDownloadCache,
        getCategories: GetMangaCategories,
        sourceManager: MangaSourceManager,
        downloadPreferences: DownloadPreferences,
    ): MangaDownloadManager =
        MangaDownloadManager(
            context = application,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaDownloadCache(
        application: Application,
        provider: MangaDownloadProvider,
        sourceManager: MangaSourceManager,
        extensionManager: MangaExtensionManager,
        storageManager: StorageManager,
    ): MangaDownloadCache =
        MangaDownloadCache(
            context = application,
            provider = provider,
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            storageManager = storageManager,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeDownloadProvider(application: Application, storageManager: StorageManager): AnimeDownloadProvider =
        AnimeDownloadProvider(context = application, storageManager = storageManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeDownloadManager(
        application: Application,
        storageManager: StorageManager,
        provider: AnimeDownloadProvider,
        cache: AnimeDownloadCache,
        getCategories: GetAnimeCategories,
        sourceManager: AnimeSourceManager,
        downloadPreferences: DownloadPreferences,
    ): AnimeDownloadManager =
        AnimeDownloadManager(
            context = application,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeDownloadCache(
        application: Application,
        provider: AnimeDownloadProvider,
        sourceManager: AnimeSourceManager,
        extensionManager: AnimeExtensionManager,
        storageManager: StorageManager,
    ): AnimeDownloadCache =
        AnimeDownloadCache(
            context = application,
            provider = provider,
            sourceManager = sourceManager,
            extensionManager = extensionManager,
            storageManager = storageManager,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeFillerSource(application: Application, network: NetworkHelper): AnimeFillerSource =
        AnimeFillerListSource(context = application, network = network)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadStateStore(application: Application): DownloadStateStore =
        DownloadStateStore(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadStrategySelector(client: OkHttpClient): DownloadStrategySelector =
        DownloadStrategySelector(client = client)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTrackerManager(application: Application): TrackerManager =
        TrackerManager(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDelayedAnimeTrackingStore(application: Application): DelayedAnimeTrackingStore =
        DelayedAnimeTrackingStore(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDelayedMangaTrackingStore(application: Application): DelayedMangaTrackingStore =
        DelayedMangaTrackingStore(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideImageSaver(application: Application): ImageSaver =
        ImageSaver(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAndroidStorageFolderProvider(application: Application): AndroidStorageFolderProvider =
        AndroidStorageFolderProvider(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalMangaSourceFileSystem(storageManager: StorageManager): LocalMangaSourceFileSystem =
        LocalMangaSourceFileSystem(storageManager = storageManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalMangaCoverManager(
        application: Application,
        fileSystem: LocalMangaSourceFileSystem,
    ): LocalMangaCoverManager =
        LocalMangaCoverManager(context = application, fileSystem = fileSystem)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalAnimeSourceFileSystem(storageManager: StorageManager): LocalAnimeSourceFileSystem =
        LocalAnimeSourceFileSystem(storageManager = storageManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalAnimeBackgroundManager(
        application: Application,
        fileSystem: LocalAnimeSourceFileSystem,
    ): LocalAnimeBackgroundManager =
        LocalAnimeBackgroundManager(context = application, fileSystem = fileSystem)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalAnimeCoverManager(
        application: Application,
        fileSystem: LocalAnimeSourceFileSystem,
    ): LocalAnimeCoverManager =
        LocalAnimeCoverManager(context = application, fileSystem = fileSystem)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalAnimeFetchTypeManager(
        application: Application,
        fileSystem: LocalAnimeSourceFileSystem,
    ): LocalAnimeFetchTypeManager =
        LocalAnimeFetchTypeManager(context = application, fileSystem = fileSystem)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLocalEpisodeThumbnailManager(
        application: Application,
        fileSystem: LocalAnimeSourceFileSystem,
    ): LocalEpisodeThumbnailManager =
        LocalEpisodeThumbnailManager(context = application, fileSystem = fileSystem)

    @Provides
    @SingleIn(AppScope::class)
    fun provideStorageManager(application: Application, storagePreferences: StoragePreferences): StorageManager =
        StorageManager(context = application, storagePreferences = storagePreferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslationEngineFactory(translationPreferences: TranslationPreferences): TranslationEngineFactory =
        TranslationEngineFactory(translationPreferences = translationPreferences)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslationStorageManager(downloadProvider: MangaDownloadProvider): TranslationStorageManager =
        TranslationStorageManager(downloadProvider = downloadProvider)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslationManager(
        application: Application,
        translationEngineFactory: TranslationEngineFactory,
        translationPreferences: TranslationPreferences,
        translationStorageManager: TranslationStorageManager,
        downloadManager: MangaDownloadManager,
        translationRunTelemetry: TranslationRunTelemetry,
    ): TranslationManager =
        TranslationManager(
            context = application,
            translationEngineFactory = translationEngineFactory,
            translationPreferences = translationPreferences,
            translationStorageManager = translationStorageManager,
            downloadManager = downloadManager,
            translationRunTelemetry = translationRunTelemetry,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslationModelCatalogRepository(): TranslationModelCatalogRepository =
        TranslationModelCatalogRepository()

    @Provides
    @SingleIn(AppScope::class)
    fun provideLightNovelPluginManager(
        application: Application,
        network: NetworkHelper,
        json: Json,
        preferences: NovelFeaturePreferences,
    ): LightNovelPluginManager =
        LightNovelPluginManager(
            context = application,
            network = network,
            json = json,
            preferences = preferences,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideLightNovelFeatureGate(
        preferences: NovelFeaturePreferences,
        pluginReadiness: LightNovelPluginReadiness,
    ): LightNovelFeatureGate =
        LightNovelFeatureGate(preferences = preferences, pluginReadiness = pluginReadiness)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLightNovelPluginLauncher(
        application: Application,
        featureGate: LightNovelFeatureGate,
    ): LightNovelPluginLauncher =
        LightNovelPluginLauncher(context = application, featureGate = featureGate)

    @Provides
    @SingleIn(AppScope::class)
    fun provideLightNovelPluginStateManager(
        application: Application,
        pluginManager: LightNovelPluginManager,
        preferences: NovelFeaturePreferences,
    ): LightNovelPluginStateManager =
        LightNovelPluginStateManager(
            appContext = application,
            pluginManager = pluginManager,
            preferences = preferences,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideExternalIntents(): ExternalIntents =
        ExternalIntents()

    @Provides
    @SingleIn(AppScope::class)
    fun provideCastManager(
        application: Application,
        network: NetworkHelper,
        playerPreferences: PlayerPreferences,
    ): CastManager =
        CastManager(context = application, network = network, playerPreferences = playerPreferences)
}
