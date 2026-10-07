package eu.kanade.tachiyomi.data.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.util.system.notify
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LiveUpdateNotificationSessionTest {

    private val context = mockk<Context>(relaxed = true)
    private val manager = mockk<NotificationManager>(relaxed = true)
    private val builder = mockk<NotificationCompat.Builder>(relaxed = true)
    private val sessions = mutableListOf<LiveUpdateNotificationSession>()
    private val tokens = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        mockkObject(NotificationReceiver)
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        every { manager.canPostPromotedNotifications() } returns true
        every { builder.setRequestPromotedOngoing(any()) } returns builder
        every { builder.setShortCriticalText(any()) } returns builder
        every { context.notify(any<Int>(), any<Notification>()) } just runs
        every { NotificationReceiver.dismissLiveUpdatePendingBroadcast(context, any()) } answers {
            tokens.add(secondArg())
            mockk()
        }
    }

    @AfterEach
    fun tearDown() {
        sessions.forEach { it.finish() }
        unmockkAll()
    }

    private fun session(id: Int = 101, sdkInt: Int = 36) =
        LiveUpdateNotificationSession(context, id, sdkInt).also {
            sessions.add(it)
            it.reset()
        }

    @Test
    fun `permission is read once per run and refreshed on reset`() {
        val session = session()
        session.applyLiveUpdate(builder, "1/3")
        every { manager.canPostPromotedNotifications() } returns false
        session.applyLiveUpdate(builder, "2/3")
        verify(exactly = 1) { manager.canPostPromotedNotifications() }
        verify(exactly = 2) { builder.setRequestPromotedOngoing(true) }
        session.reset()
        session.applyLiveUpdate(builder, "3/3")
        verify(exactly = 2) { manager.canPostPromotedNotifications() }
        verify { builder.setRequestPromotedOngoing(false) }
        verify { builder.setShortCriticalText(null) }
    }

    @Test
    fun `older Android posts without promotion permission or a delete intent`() {
        val session = session(sdkInt = 35)
        session.applyLiveUpdate(builder, "1/3")
        session.show(builder)
        verify(exactly = 0) { manager.canPostPromotedNotifications() }
        verify(exactly = 0) { builder.setRequestPromotedOngoing(any()) }
        verify(exactly = 0) { builder.setDeleteIntent(any()) }
        verify(exactly = 1) { context.notify(101, any<Notification>()) }
    }

    @Test
    fun `dismissed run cannot repost until reset`() {
        val session = session()
        session.show(builder)
        LiveUpdateNotificationSession.dismiss(tokens.last())
        clearStaticMockk(
            Class.forName("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt").kotlin,
            answers = false,
        )
        session.show(builder)
        verify(exactly = 0) { context.notify(any<Int>(), any<Notification>()) }
        session.reset()
        session.show(builder)
        verify(exactly = 1) { context.notify(101, any<Notification>()) }
    }

    @Test
    fun `finished run cannot repost until reset`() {
        val session = session()
        session.show(builder)
        session.finish()
        clearStaticMockk(
            Class.forName("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt").kotlin,
            answers = false,
        )
        session.show(builder)
        verify(exactly = 0) { context.notify(any<Int>(), any<Notification>()) }
        session.reset()
        session.show(builder)
        verify(exactly = 1) { context.notify(101, any<Notification>()) }
    }

    @Test
    fun `old token cannot dismiss a new run`() {
        val session = session()
        session.show(builder)
        val oldToken = tokens.last()
        session.reset()
        session.show(builder)
        assertNotEquals(oldToken, tokens.last())
        LiveUpdateNotificationSession.dismiss(oldToken)
        clearStaticMockk(
            Class.forName("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt").kotlin,
            answers = false,
        )
        session.show(builder)
        verify(exactly = 1) { context.notify(101, any<Notification>()) }
    }

    @Test
    fun `dismissal suppresses the posted snapshot but not a later run or another ID`() {
        val manga = session()
        val anime = session()
        manga.show(builder)
        anime.show(builder)
        val postedToken = tokens.last()
        val laterRun = session()
        val download = session(id = 201)
        LiveUpdateNotificationSession.dismiss(postedToken)
        clearStaticMockk(
            Class.forName("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt").kotlin,
            answers = false,
        )
        manga.show(builder)
        anime.show(builder)
        verify(exactly = 0) { context.notify(101, any<Notification>()) }
        laterRun.show(builder)
        download.show(builder)
        verify(exactly = 1) { context.notify(101, any<Notification>()) }
        verify(exactly = 1) { context.notify(201, any<Notification>()) }
    }

    @Test
    fun `stale owner token cannot dismiss the current shared ID owner`() {
        val manga = session()
        val anime = session()
        manga.show(builder)
        val staleToken = tokens.last()
        anime.show(builder)
        LiveUpdateNotificationSession.dismiss(staleToken)
        clearStaticMockk(
            Class.forName("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt").kotlin,
            answers = false,
        )
        manga.show(builder)
        verify(exactly = 0) { context.notify(101, any<Notification>()) }
        anime.show(builder)
        verify(exactly = 1) { context.notify(101, any<Notification>()) }
    }
}
