package eu.kanade.tachiyomi.data.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotificationMode
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.library.anime.AnimeMetadataUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.library.manga.MangaMetadataUpdateJob
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.core.common.i18n.stringResource
import java.util.UUID

class LibraryProgressNotificationTest {
    private val context = mockk<Context>(relaxed = true)
    private val security = mockk<SecurityPreferences>(relaxed = true)
    private val animeLibraryCancel = mockk<PendingIntent>()
    private val mangaLibraryCancel = mockk<PendingIntent>()
    private val cancelActions = mutableMapOf<Notification, PendingIntent>()
    private val activeNotifications = mutableMapOf<Int, Notification>()

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        mockkObject(NotificationReceiver, AppGraphHolder, WorkManager.Companion)
        val graph = mockk<AppGraph>(relaxed = true)
        every { AppGraphHolder.graph } returns graph
        every { graph.securityPreferences } returns security
        every { security.hideNotificationContent().get() } returns true
        every { context.stringResource(any()) } returns "progress"
        every { context.stringResource(any(), *anyVararg()) } returns "progress"
        every { NotificationReceiver.cancelAnimelibUpdatePendingBroadcast(context) } returns animeLibraryCancel
        every { NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context) } returns mangaLibraryCancel
        every { context.notify(any<Int>(), any<Notification>()) } answers {
            activeNotifications[secondArg()] = thirdArg()
        }
        every { context.cancelNotification(any()) } answers {
            activeNotifications.remove(secondArg<Int>())
        }
        every { context.notificationBuilder(any(), any()) } answers {
            val builder = mockk<NotificationCompat.Builder>(relaxed = true)
            val notification = mockk<Notification>()
            every { builder.build() } returns notification
            every { builder.addAction(any(), any(), any()) } answers {
                cancelActions[notification] = thirdArg()
                builder
            }
            arg<(NotificationCompat.Builder.() -> Unit)?>(2)?.invoke(builder)
            builder
        }
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @ParameterizedTest
    @ValueSource(strings = ["anime", "manga"])
    fun `cover notification cancel targets its own work request`(media: String) = runTest {
        val workId = UUID.randomUUID()
        val workerParameters = mockk<WorkerParameters>(relaxed = true)
        val workManager = mockk<WorkManager>()
        val coverCancel = mockk<PendingIntent>()
        every { workerParameters.id } returns workId
        every { WorkManager.getInstance(context) } returns workManager
        every { workManager.createCancelPendingIntent(workId) } returns coverCancel

        val foregroundInfo = when (media) {
            "anime" -> AnimeMetadataUpdateJob(context, workerParameters).getForegroundInfo()
            else -> MangaMetadataUpdateJob(context, workerParameters).getForegroundInfo()
        }

        assertSame(coverCancel, cancelActions[foregroundInfo.notification])
    }

    @ParameterizedTest
    @CsvSource("Standard, anime", "Standard, manga", "Live, anime", "Live, manga")
    fun `finishing a library update leaves the other cancel action posted`(
        mode: LibraryUpdateNotificationMode,
        firstToFinish: String,
    ) {
        val anime = AnimeLibraryUpdateNotifier(context, security, mockk(), mode)
        val manga = MangaLibraryUpdateNotifier(context, security, mockk(), mode)
        anime.onUpdateStarted()
        manga.onUpdateStarted()
        anime.showProgressNotification(emptyList(), 1, 2)
        manga.showProgressNotification(emptyList(), 1, 2)

        val survivingCancel = if (firstToFinish == "anime") {
            anime.cancelProgressNotification()
            mangaLibraryCancel
        } else {
            manga.cancelProgressNotification()
            animeLibraryCancel
        }

        assertEquals(listOf(survivingCancel), activeNotifications.values.map { cancelActions[it] })
    }
}
