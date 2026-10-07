package eu.kanade.tachiyomi.di

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import eu.kanade.domain.CategoryBindings
import eu.kanade.domain.DomainModule
import eu.kanade.domain.EntryBindings
import eu.kanade.domain.ItemBindings
import eu.kanade.domain.SYDomainModule
import eu.kanade.domain.SourceBindings
import eu.kanade.domain.TrackingBindings
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload
import eu.kanade.domain.download.manga.interactor.DeleteChapterDownload
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.PopulateFillerMarks
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.interactor.TrackSyncConflictResolver
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.service.TrackerSyncCoordinator
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.domain.update.UpdatePromptPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
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
import eu.kanade.tachiyomi.data.filler.AnimeFillerSource
import eu.kanade.tachiyomi.data.library.LibraryUpdateSummaryStore
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationEngineFactory
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationRunTelemetry
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
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
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
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.OkHttpClient
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.data.Database
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags
import tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.ResetMangaCategoryFlags
import tachiyomi.domain.category.manga.interactor.UpdateMangaCategory
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.items.season.interactor.ShouldUpdateDbSeason
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.presentation.widget.di.WidgetGraph
import tachiyomi.source.local.di.LocalSourceGraph
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        AppModule::class,
        ViewModelBindings::class,
        PreferenceModule::class,
        DomainModule::class,
        CategoryBindings::class,
        EntryBindings::class,
        TrackingBindings::class,
        ItemBindings::class,
        SourceBindings::class,
        SYDomainModule::class,
    ],
)
interface AppGraph : LocalSourceGraph, WidgetGraph, ViewModelGraph {

    override val application: Application
    val database: Database
    val animeDatabase: AnimeDatabase
    val mangaDatabaseHandler: MangaDatabaseHandler
    val animeDatabaseHandler: AnimeDatabaseHandler
    override val json: Json
    override val xml: XML
    val protoBuf: ProtoBuf
    val chapterCache: ChapterCache
    val mangaCoverCache: MangaCoverCache
    val animeCoverCache: AnimeCoverCache
    val animeBackgroundCache: AnimeBackgroundCache
    val networkHelper: NetworkHelper
    val okHttpClient: OkHttpClient
    val javaScriptEngine: JavaScriptEngine
    val mangaSourceManager: MangaSourceManager
    val animeSourceManager: AnimeSourceManager
    val mangaExtensionManager: MangaExtensionManager
    val animeExtensionManager: AnimeExtensionManager
    val mangaDownloadProvider: MangaDownloadProvider
    val mangaDownloadManager: MangaDownloadManager
    val mangaDownloadCache: MangaDownloadCache
    val animeDownloadProvider: AnimeDownloadProvider
    val animeDownloadManager: AnimeDownloadManager
    val animeDownloadCache: AnimeDownloadCache
    val animeFillerSource: AnimeFillerSource
    val downloadStateStore: DownloadStateStore
    val downloadStrategySelector: DownloadStrategySelector
    val multiThreadDownloader: MultiThreadDownloader
    val trackerManager: TrackerManager
    val delayedAnimeTrackingStore: DelayedAnimeTrackingStore
    val delayedMangaTrackingStore: DelayedMangaTrackingStore
    val androidStorageFolderProvider: AndroidStorageFolderProvider
    val localMangaSourceFileSystem: LocalMangaSourceFileSystem
    val localMangaCoverManager: LocalMangaCoverManager
    val localAnimeSourceFileSystem: LocalAnimeSourceFileSystem
    val localAnimeBackgroundManager: LocalAnimeBackgroundManager
    val localAnimeCoverManager: LocalAnimeCoverManager
    val localAnimeFetchTypeManager: LocalAnimeFetchTypeManager
    val localEpisodeThumbnailManager: LocalEpisodeThumbnailManager
    val storageManager: StorageManager
    val translationEngineFactory: TranslationEngineFactory
    val translationStorageManager: TranslationStorageManager
    val translationRunTelemetry: TranslationRunTelemetry
    val translationManager: TranslationManager
    val translationModelCatalogRepository: TranslationModelCatalogRepository
    val lightNovelPluginManager: LightNovelPluginManager
    val lightNovelPluginReadiness: LightNovelPluginReadiness
    val lightNovelFeatureGate: LightNovelFeatureGate
    val lightNovelPluginLauncher: LightNovelPluginLauncher
    val lightNovelPluginStateManager: LightNovelPluginStateManager
    val externalIntents: ExternalIntents
    val castManager: CastManager
    val preferenceStore: PreferenceStore
    val libraryUpdateSummaryStore: LibraryUpdateSummaryStore
    val networkPreferences: NetworkPreferences
    val sourcePreferences: SourcePreferences
    override val securityPreferences: SecurityPreferences
    val libraryPreferences: LibraryPreferences
    val readerPreferences: ReaderPreferences
    val playerPreferences: PlayerPreferences
    val gesturePreferences: GesturePreferences
    val decoderPreferences: DecoderPreferences
    val subtitlePreferences: SubtitlePreferences
    val audioPreferences: AudioPreferences
    val advancedPlayerPreferences: AdvancedPlayerPreferences
    val trackPreferences: TrackPreferences
    val downloadPreferences: DownloadPreferences
    val backupPreferences: BackupPreferences
    val storagePreferences: StoragePreferences
    val uiPreferences: UiPreferences
    val basePreferences: BasePreferences
    val translationPreferences: TranslationPreferences
    val novelFeaturePreferences: NovelFeaturePreferences
    val animeCategoryRepository: AnimeCategoryRepository
    val getAnimeCategories: GetAnimeCategories
    val resetAnimeCategoryFlags: ResetAnimeCategoryFlags
    val updateAnimeCategory: UpdateAnimeCategory
    val mangaCategoryRepository: MangaCategoryRepository
    val getMangaCategories: GetMangaCategories
    val resetMangaCategoryFlags: ResetMangaCategoryFlags
    val updateMangaCategory: UpdateMangaCategory
    val animeRepository: AnimeRepository
    val getAnimeFavorites: GetAnimeFavorites
    val getLibraryAnime: GetLibraryAnime
    val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId
    val getAnime: GetAnime
    val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId
    val resetAnimeViewerFlags: ResetAnimeViewerFlags
    val animeFetchInterval: AnimeFetchInterval
    val networkToLocalAnime: NetworkToLocalAnime
    val updateAnime: UpdateAnime
    val shouldUpdateDbSeason: ShouldUpdateDbSeason
    val syncSeasonsWithSource: SyncSeasonsWithSource
    val mangaRepository: MangaRepository
    val getMangaFavorites: GetMangaFavorites
    val getLibraryManga: GetLibraryManga
    val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId
    val getManga: GetManga
    val resetMangaViewerFlags: ResetMangaViewerFlags
    val mangaFetchInterval: MangaFetchInterval
    val networkToLocalManga: NetworkToLocalManga
    val updateManga: UpdateManga
    val releaseService: ReleaseService
    val getApplicationRelease: GetApplicationRelease
    val updatePromptPreferences: UpdatePromptPreferences
    val updatePromptGatekeeper: UpdatePromptGatekeeper
    val animeTrackRepository: AnimeTrackRepository
    val trackEpisode: TrackEpisode
    val addAnimeTracks: AddAnimeTracks
    val getAnimeTracks: GetAnimeTracks
    val insertAnimeTrack: InsertAnimeTrack
    val syncEpisodeProgressWithTrack: SyncEpisodeProgressWithTrack
    val mangaTrackRepository: MangaTrackRepository
    val trackChapter: TrackChapter
    val addMangaTracks: AddMangaTracks
    val getMangaTracks: GetMangaTracks
    val insertMangaTrack: InsertMangaTrack
    val syncChapterProgressWithTrack: SyncChapterProgressWithTrack
    val trackSyncConflictResolver: TrackSyncConflictResolver
    val refreshAllMangaTracks: RefreshAllMangaTracks
    val refreshAllAnimeTracks: RefreshAllAnimeTracks
    val trackerSyncCoordinator: TrackerSyncCoordinator
    val episodeRepository: EpisodeRepository
    val getEpisode: GetEpisode
    val getEpisodesByAnimeId: GetEpisodesByAnimeId
    val updateEpisode: UpdateEpisode
    val populateFillerMarks: PopulateFillerMarks
    val shouldUpdateDbEpisode: ShouldUpdateDbEpisode
    val syncEpisodesWithSource: SyncEpisodesWithSource
    val filterEpisodesForDownload: FilterEpisodesForDownload
    val chapterRepository: ChapterRepository
    val getChapter: GetChapter
    val getChaptersByMangaId: GetChaptersByMangaId
    val updateChapter: UpdateChapter
    val shouldUpdateDbChapter: ShouldUpdateDbChapter
    val syncChaptersWithSource: SyncChaptersWithSource
    val filterChaptersForDownload: FilterChaptersForDownload
    val animeHistoryRepository: AnimeHistoryRepository
    val getAnimeHistory: GetAnimeHistory
    val upsertAnimeHistory: UpsertAnimeHistory
    val deleteEpisodeDownload: DeleteEpisodeDownload
    val mangaHistoryRepository: MangaHistoryRepository
    val getMangaHistory: GetMangaHistory
    val getTotalReadDuration: GetTotalReadDuration
    val deleteChapterDownload: DeleteChapterDownload
    val animeUpdatesRepository: AnimeUpdatesRepository
    override val getAnimeUpdates: GetAnimeUpdates
    val mangaUpdatesRepository: MangaUpdatesRepository
    override val getMangaUpdates: GetMangaUpdates
    val animeSourceRepository: AnimeSourceRepository
    val animeStubSourceRepository: AnimeStubSourceRepository
    val getLanguagesWithAnimeSources: GetLanguagesWithAnimeSources
    val getAnimeSourcesWithFavoriteCount: GetAnimeSourcesWithFavoriteCount
    val mangaSourceRepository: MangaSourceRepository
    val mangaStubSourceRepository: MangaStubSourceRepository
    val getLanguagesWithMangaSources: GetLanguagesWithMangaSources
    val getMangaSourcesWithFavoriteCount: GetMangaSourcesWithFavoriteCount
    val setMigrateSorting: SetMigrateSorting
    val trustAnimeExtension: TrustAnimeExtension
    val trustMangaExtension: TrustMangaExtension
    val extensionRepoService: ExtensionRepoService
    val animeExtensionRepoRepository: AnimeExtensionRepoRepository
    val getAnimeExtensionRepo: GetAnimeExtensionRepo
    val getAnimeExtensionRepoCount: GetAnimeExtensionRepoCount
    val createAnimeExtensionRepo: CreateAnimeExtensionRepo
    val deleteAnimeExtensionRepo: DeleteAnimeExtensionRepo
    val replaceAnimeExtensionRepo: ReplaceAnimeExtensionRepo
    val updateAnimeExtensionRepo: UpdateAnimeExtensionRepo
    val getAnimeIncognitoState: GetAnimeIncognitoState
    val mangaExtensionRepoRepository: MangaExtensionRepoRepository
    val getMangaExtensionRepo: GetMangaExtensionRepo
    val getMangaExtensionRepoCount: GetMangaExtensionRepoCount
    val createMangaExtensionRepo: CreateMangaExtensionRepo
    val deleteMangaExtensionRepo: DeleteMangaExtensionRepo
    val replaceMangaExtensionRepo: ReplaceMangaExtensionRepo
    val updateMangaExtensionRepo: UpdateMangaExtensionRepo
    val getMangaIncognitoState: GetMangaIncognitoState
    val updateMangaFromRemote: UpdateMangaFromRemote
    val customButtonRepository: CustomButtonRepository
    val getCustomButtons: GetCustomButtons

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides application: Application): AppGraph
    }
}
