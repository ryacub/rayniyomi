package eu.kanade.tachiyomi.data.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadNotifier
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadNotifier
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotificationMode
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateNotifier
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.clearLiveUpdate
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import eu.kanade.tachiyomi.util.system.setLiveUpdate
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga

class PromotedProgressNotifiersTest {

    private val context = mockk<Context>(relaxed = true)
    private val builder = mockk<NotificationCompat.Builder>(relaxed = true)
    private val security = mockk<SecurityPreferences>(relaxed = true)
    private val manager = mockk<NotificationManager>(relaxed = true)
    private var hideContent = false
    private var promoted = false
    private var chipText: String? = null

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.LiveUpdateNotificationKt")
        mockkObject(NotificationReceiver, NotificationHandler, AppGraphHolder)
        val graph = mockk<AppGraph>(relaxed = true)
        every { AppGraphHolder.graph } returns graph
        every { graph.securityPreferences } returns security
        every { security.hideNotificationContent().get() } answers { hideContent }
        every { context.stringResource(any()) } returns "progress"
        every { context.stringResource(any(), *anyVararg()) } returns "progress"
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.canPostPromotedNotifications() } returns true
        every { context.notify(any<Int>(), any<Notification>()) } just runs
        every { context.cancelNotification(any()) } just runs
        every { NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.cancelAnimelibUpdatePendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.pauseDownloadsPendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.pauseAnimeDownloadsPendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.openMangaEntryPendingActivity(context, any()) } returns mockk()
        every { NotificationReceiver.openAnimeEntryPendingActivity(context, any()) } returns mockk()
        every { NotificationReceiver.resumeDownloadsPendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.resumeAnimeDownloadsPendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.clearDownloadsPendingBroadcast(context) } returns mockk()
        every { NotificationReceiver.clearAnimeDownloadsPendingBroadcast(context) } returns mockk()
        every { NotificationHandler.openDownloadManagerPendingActivity(context) } returns mockk()
        every { NotificationHandler.openAnimeDownloadManagerPendingActivity(context) } returns mockk()
        every { context.notificationBuilder(any(), any()) } answers {
            arg<(NotificationCompat.Builder.() -> Unit)?>(2)?.invoke(builder)
            builder
        }
        every { builder.setContentTitle(any()) } returns builder
        every { builder.setProgress(any(), any(), any()) } returns builder
        every { builder.setOngoing(any()) } returns builder
        every { builder.setRequestPromotedOngoing(any()) } answers {
            promoted = firstArg()
            builder
        }
        every { builder.setShortCriticalText(any()) } answers {
            chipText = firstArg()
            builder
        }
        every { builder.setLiveUpdate(context, any(), Build.VERSION.SDK_INT) } answers {
            builder.setLiveUpdate(context, arg(2), sdkInt = 36)
        }
        every { builder.clearLiveUpdate(Build.VERSION.SDK_INT) } answers {
            builder.clearLiveUpdate(sdkInt = 36)
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @ParameterizedTest
    @ValueSource(strings = ["manga", "anime"])
    fun `library progress promotes live mode and clears private titles`(media: String) {
        val manga = mockk<Manga>(relaxed = true) { every { title } returns "Manga title" }
        val anime = mockk<Anime>(relaxed = true) { every { title } returns "Anime title" }
        val show: (Int) -> Unit = when (media) {
            "manga" -> {
                val notifier = MangaLibraryUpdateNotifier(
                    context,
                    security,
                    mockk(),
                    LibraryUpdateNotificationMode.Live,
                )
                val update: (Int) -> Unit = { current ->
                    notifier.showProgressNotification(listOf(manga), current, 19)
                }
                update
            }
            else -> {
                val notifier = AnimeLibraryUpdateNotifier(
                    context,
                    security,
                    mockk(),
                    LibraryUpdateNotificationMode.Live,
                )
                val update: (Int) -> Unit = { current ->
                    notifier.showProgressNotification(listOf(anime), current, 19)
                }
                update
            }
        }
        show(2)
        assertEquals(true, promoted)
        assertEquals("2/19", chipText)
        verify { builder.setProgress(19, 2, false) }
        hideContent = true
        show(3)
        assertEquals("3/19", chipText)
        verify { builder.setStyle(null) }

        clearMocks(builder, answers = false)
        when (media) {
            "manga" -> MangaLibraryUpdateNotifier(context, security, mockk())
                .showProgressNotification(listOf(manga), 2, 19)
            else -> AnimeLibraryUpdateNotifier(context, security, mockk())
                .showProgressNotification(listOf(anime), 2, 19)
        }
        verify(exactly = 0) { builder.setRequestPromotedOngoing(any()) }
        verify(exactly = 0) { builder.setShortCriticalText(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["manga", "anime"])
    fun `download progress clears and restores live updates across lifecycle`(media: String) {
        val manga = mockk<MangaDownload>(relaxed = true) {
            every { pages } returns List(19) { mockk(relaxed = true) }
            every { downloadedImages } returns 2
            every { this@mockk.manga.title } returns "Manga title"
            every { chapter.name } returns "Chapter 1"
        }
        val anime = mockk<AnimeDownload>(relaxed = true) {
            every { progress } returns 25
            every { this@mockk.anime.title } returns "Anime title"
            every { episode.name } returns "Episode 1"
        }
        val mangaNotifier = MangaDownloadNotifier(context)
        val animeNotifier = AnimeDownloadNotifier(context)
        fun progress() = when (media) {
            "manga" -> mangaNotifier.onProgressChange(manga)
            else -> animeNotifier.onProgressChange(anime)
        }
        progress()
        assertEquals(true, promoted)
        assertEquals(if (media == "manga") "2/19" else "25%", chipText)
        when (media) {
            "manga" -> mangaNotifier.onPaused()
            else -> animeNotifier.onPaused()
        }
        assertEquals(false, promoted)
        assertEquals(null, chipText)
        verify { builder.setOngoing(false) }
        progress()
        assertEquals(true, promoted)
        when (media) {
            "manga" -> mangaNotifier.onWarning("No network")
            else -> animeNotifier.onWarning("No network")
        }
        assertEquals(false, promoted)
        assertEquals(null, chipText)
        progress()
        assertEquals(true, promoted)
        when (media) {
            "manga" -> mangaNotifier.onComplete()
            else -> animeNotifier.onComplete()
        }
        assertEquals(false, promoted)
        assertEquals(null, chipText)
    }
}
