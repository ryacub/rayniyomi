package eu.kanade.tachiyomi.data.download.manga

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource

class MangaDownloadNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: MangaDownloadNotifier

    @BeforeEach
    fun setUp() {
        context = mockk<Context>(relaxed = true)

        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        mockkObject(NotificationHandler)

        val mockBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { context.notificationBuilder(any(), any()) } returns mockBuilder
        every { context.notify(any<Int>(), any<Notification>()) } just runs
        every { context.stringResource(any()) } returns "mocked"
        every { NotificationHandler.openDownloadManagerPendingActivity(context) } returns mockk()

        notifier = MangaDownloadNotifier(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        unmockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        unmockkObject(NotificationHandler)
    }

    @Test
    fun `onCrashThresholdExceeded shows notification with crash threshold ID`() {
        notifier.onCrashThresholdExceeded()

        verify { context.notify(Notifications.ID_DOWNLOAD_CHAPTER_CRASH_THRESHOLD, any<Notification>()) }
    }

    @Test
    fun `onCrashThresholdExceeded uses download manager click intent`() {
        notifier.onCrashThresholdExceeded()

        verify { NotificationHandler.openDownloadManagerPendingActivity(context) }
    }
}
