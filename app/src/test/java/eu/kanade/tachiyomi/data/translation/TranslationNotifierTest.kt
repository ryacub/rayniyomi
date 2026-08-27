package eu.kanade.tachiyomi.data.translation

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR

class TranslationNotifierTest {
    private val chapterId = 100L
    private val otherChapterId = 200L

    private lateinit var context: Context
    private lateinit var notifier: TranslationNotifier

    @BeforeEach
    fun setUp() {
        context = mockk<Context>(relaxed = true)

        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")

        val mockBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { context.notificationBuilder(any(), any()) } returns mockBuilder
        every { context.notify(any<Int>(), any<Notification>()) } just runs
        every { context.cancelNotification(any()) } just runs
        every { context.stringResource(any()) } returns "mocked"
        every { context.stringResource(any(), *anyVararg()) } returns "mocked"

        notifier = TranslationNotifier(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        unmockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
    }

    private fun titles(vararg entries: Pair<Long, String>): Map<Long, String> =
        if (entries.isEmpty()) {
            mapOf(chapterId to "Test Manga - Chapter 1")
        } else {
            mapOf(*entries)
        }

    @Test
    fun `posts a progress notification when a chapter starts translating`() {
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Translating(0, 32)),
            titles(),
        )

        verify(exactly = 1) {
            context.notify(Notifications.translationProgressId(chapterId), any<Notification>())
        }
    }

    @Test
    fun `updates the progress notification when the page count advances`() {
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translating(0, 32)), titles())
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translating(1, 32)), titles())

        verify(exactly = 2) {
            context.notify(Notifications.translationProgressId(chapterId), any<Notification>())
        }
    }

    @Test
    fun `does not repost when an unrelated chapter changes and this chapter is unchanged`() {
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Translating(0, 32)),
            titles(),
        )
        notifier.onStatesChanged(
            mapOf(
                chapterId to TranslationState.Translating(0, 32),
                otherChapterId to TranslationState.Translating(0, 10),
            ),
            titles(
                chapterId to "Test Manga - Chapter 1",
                otherChapterId to "Test Manga - Chapter 2",
            ),
        )

        verify(exactly = 1) {
            context.notify(Notifications.translationProgressId(chapterId), any<Notification>())
        }
        verify(exactly = 1) {
            context.notify(Notifications.translationProgressId(otherChapterId), any<Notification>())
        }
    }

    @Test
    fun `posts a completion notification once when a chapter reaches Translated`() {
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translating(0, 2)), titles())
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translated), titles())

        verify(exactly = 1) { context.cancelNotification(Notifications.translationProgressId(chapterId)) }
        verify(exactly = 1) {
            context.notify(Notifications.translationCompleteId(chapterId), any<Notification>())
        }
    }

    @Test
    fun `does not repost the completion notification on later emissions`() {
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translated), titles())
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translated), titles())

        verify(exactly = 1) {
            context.notify(Notifications.translationCompleteId(chapterId), any<Notification>())
        }
    }

    @Test
    fun `posts an error notification carrying the failure message when a chapter reaches Error`() {
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Error("API rate limit exceeded")),
            titles(),
        )

        verify(exactly = 1) { context.cancelNotification(Notifications.translationProgressId(chapterId)) }
        verify(exactly = 1) {
            context.notify(Notifications.translationErrorId(chapterId), any<Notification>())
        }
        verify { context.stringResource(AYMR.strings.translation_error, "API rate limit exceeded") }
    }

    @Test
    fun `posts an incomplete notification with resolved and unresolved pages`() {
        val state = TranslationState.Incomplete(
            resolvedPages = 3,
            totalPages = 5,
            unresolvedPages = listOf(4, 5),
            reason = "Page 4 could not be translated",
        )

        notifier.onStatesChanged(mapOf(chapterId to state), titles())

        verify(exactly = 1) {
            context.notify(Notifications.translationErrorId(chapterId), any<Notification>())
        }
        verify {
            context.stringResource(AYMR.strings.translation_incomplete, 3, 5, "4, 5")
        }
    }

    @Test
    fun `clears stale incomplete notifications when translation resumes and completes`() {
        notifier.onStatesChanged(
            mapOf(
                chapterId to TranslationState.Incomplete(3, 5, listOf(4, 5), "Page 4 failed"),
            ),
            titles(),
        )
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Translating(3, 5)),
            titles(),
        )
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translated), titles())

        verify(exactly = 2) { context.cancelNotification(Notifications.translationErrorId(chapterId)) }
        verify(exactly = 1) { context.notify(Notifications.translationCompleteId(chapterId), any<Notification>()) }
    }

    @Test
    fun `does not repost the error notification on later emissions`() {
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Error("API rate limit exceeded")),
            titles(),
        )
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Error("API rate limit exceeded")),
            titles(),
        )

        verify(exactly = 1) {
            context.notify(Notifications.translationErrorId(chapterId), any<Notification>())
        }
    }

    @Test
    fun `cancels the notification when a chapter key disappears from the map`() {
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translating(0, 2)), titles())
        notifier.onStatesChanged(emptyMap(), emptyMap())

        verify(exactly = 1) { context.cancelNotification(Notifications.translationProgressId(chapterId)) }
    }

    @Test
    fun `cancels every notification when the map becomes empty`() {
        notifier.onStatesChanged(
            mapOf(
                chapterId to TranslationState.Translating(0, 2),
                otherChapterId to TranslationState.Error("boom"),
            ),
            titles(
                chapterId to "Test Manga - Chapter 1",
                otherChapterId to "Test Manga - Chapter 2",
            ),
        )
        notifier.onStatesChanged(emptyMap(), emptyMap())

        verify(exactly = 1) { context.cancelNotification(Notifications.translationProgressId(chapterId)) }
        verify(exactly = 1) { context.cancelNotification(Notifications.translationErrorId(otherChapterId)) }
    }

    @Test
    fun `gives two concurrent chapters two different notification ids`() {
        val notifiedIds = mutableListOf<Int>()
        every { context.notify(any<Int>(), any<Notification>()) } answers {
            notifiedIds.add(secondArg())
        }

        notifier.onStatesChanged(
            mapOf(
                chapterId to TranslationState.Translating(0, 2),
                otherChapterId to TranslationState.Translating(0, 5),
            ),
            titles(
                chapterId to "Test Manga - Chapter 1",
                otherChapterId to "Test Manga - Chapter 2",
            ),
        )

        assertEquals(2, notifiedIds.size)
        assertEquals(2, notifiedIds.distinct().size)
    }

    @Test
    fun `reposts progress when a chapter translates a second time after cancellation`() {
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translating(0, 2)), titles())
        notifier.onStatesChanged(emptyMap(), emptyMap())
        notifier.onStatesChanged(mapOf(chapterId to TranslationState.Translating(0, 2)), titles())

        verify(exactly = 2) {
            context.notify(Notifications.translationProgressId(chapterId), any<Notification>())
        }
    }

    @Test
    fun `falls back to a generic title when the title map has no entry for the chapter`() {
        notifier.onStatesChanged(
            mapOf(chapterId to TranslationState.Translating(0, 2)),
            emptyMap(),
        )

        verify { context.stringResource(AYMR.strings.pref_category_translation) }
        verify(exactly = 1) {
            context.notify(Notifications.translationProgressId(chapterId), any<Notification>())
        }
    }
}
