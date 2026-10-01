package eu.kanade.tachiyomi.di

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.security.SecurePreferenceStore
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.ui.player.settings.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.storage.AndroidStorageFolderProvider
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StoragePreferences

@BindingContainer
object PreferenceModule {

    @Provides
    @SingleIn(AppScope::class)
    fun providePreferenceStore(application: Application): PreferenceStore =
        AndroidPreferenceStore(context = application)

    @Provides
    @SingleIn(AppScope::class)
    fun provideNetworkPreferences(preferenceStore: PreferenceStore): NetworkPreferences =
        NetworkPreferences(preferenceStore = preferenceStore, verboseLogging = isDebugBuildType)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSourcePreferences(preferenceStore: PreferenceStore): SourcePreferences =
        SourcePreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSecurityPreferences(preferenceStore: PreferenceStore): SecurityPreferences =
        SecurityPreferences(SecurePreferenceStore(preferenceStore))

    @Provides
    @SingleIn(AppScope::class)
    fun provideLibraryPreferences(preferenceStore: PreferenceStore): LibraryPreferences =
        LibraryPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideReaderPreferences(preferenceStore: PreferenceStore): ReaderPreferences =
        ReaderPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlayerPreferences(preferenceStore: PreferenceStore): PlayerPreferences =
        PlayerPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideGesturePreferences(preferenceStore: PreferenceStore): GesturePreferences =
        GesturePreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideDecoderPreferences(preferenceStore: PreferenceStore): DecoderPreferences =
        DecoderPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideSubtitlePreferences(preferenceStore: PreferenceStore): SubtitlePreferences =
        SubtitlePreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAudioPreferences(preferenceStore: PreferenceStore): AudioPreferences =
        AudioPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAdvancedPlayerPreferences(preferenceStore: PreferenceStore): AdvancedPlayerPreferences =
        AdvancedPlayerPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTrackPreferences(preferenceStore: PreferenceStore): TrackPreferences =
        TrackPreferences(SecurePreferenceStore(preferenceStore))

    @Provides
    @SingleIn(AppScope::class)
    fun provideDownloadPreferences(preferenceStore: PreferenceStore): DownloadPreferences =
        DownloadPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideBackupPreferences(preferenceStore: PreferenceStore): BackupPreferences =
        BackupPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideStoragePreferences(
        folderProvider: AndroidStorageFolderProvider,
        preferenceStore: PreferenceStore,
    ): StoragePreferences =
        StoragePreferences(folderProvider = folderProvider, preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUiPreferences(preferenceStore: PreferenceStore): UiPreferences =
        UiPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideBasePreferences(application: Application, preferenceStore: PreferenceStore): BasePreferences =
        BasePreferences(context = application, preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTranslationPreferences(preferenceStore: PreferenceStore): TranslationPreferences =
        TranslationPreferences(SecurePreferenceStore(preferenceStore))

    @Provides
    @SingleIn(AppScope::class)
    fun provideNovelFeaturePreferences(preferenceStore: PreferenceStore): NovelFeaturePreferences =
        NovelFeaturePreferences(preferenceStore = preferenceStore)
}
