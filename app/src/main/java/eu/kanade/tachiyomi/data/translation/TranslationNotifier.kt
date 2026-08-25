package eu.kanade.tachiyomi.data.translation

import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR

/**
 * Posts system notifications for chapter translation progress.
 *
 * Terminal states never leave the state map, so [lastPosted] remembers the last state posted for
 * each chapter and skips reposts of an unchanged state. Cancel and delete remove the key instead
 * of emitting a state, so removed keys are found by diffing against [previousKeys].
 *
 * The caller invokes [onStatesChanged] once per emission of the combined state and title flows.
 */
class TranslationNotifier(private val context: Context) {

    private val lastPosted = mutableMapOf<Long, TranslationState>()
    private var previousKeys: Set<Long> = emptySet()

    fun onStatesChanged(states: Map<Long, TranslationState>, titles: Map<Long, String>) {
        (previousKeys - states.keys).forEach { chapterId ->
            context.cancelNotification(Notifications.translationProgressId(chapterId))
            context.cancelNotification(Notifications.translationCompleteId(chapterId))
            context.cancelNotification(Notifications.translationErrorId(chapterId))
            lastPosted.remove(chapterId)
        }
        previousKeys = states.keys

        states.forEach { (chapterId, state) ->
            if (lastPosted[chapterId] == state) return@forEach
            lastPosted[chapterId] = state

            when (state) {
                TranslationState.Idle -> Unit // Idle chapters are absent from the map.
                is TranslationState.Translating -> showProgress(chapterId, state, titles[chapterId])
                TranslationState.Translated -> showComplete(chapterId, titles[chapterId])
                is TranslationState.Error -> showError(chapterId, state.message, titles[chapterId])
            }
        }
    }

    private fun titleFor(chapterId: Long, title: String?): String =
        title ?: context.stringResource(AYMR.strings.pref_category_translation)

    private fun showProgress(
        chapterId: Long,
        state: TranslationState.Translating,
        title: String?,
    ) {
        // Resolve all text before entering the builder lambda.
        val titleText = titleFor(chapterId, title)
        val contentText =
            context.stringResource(AYMR.strings.translation_progress, state.currentPage, state.totalPages)

        context.notify(Notifications.translationProgressId(chapterId), Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(titleText)
            setContentText(contentText)
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setAutoCancel(false)
            setOnlyAlertOnce(true)
            setProgress(state.totalPages, state.currentPage, false)
        }
    }

    private fun showComplete(chapterId: Long, title: String?) {
        context.cancelNotification(Notifications.translationProgressId(chapterId))

        val titleText = titleFor(chapterId, title)
        val contentText = context.stringResource(AYMR.strings.translation_complete)

        context.notify(Notifications.translationCompleteId(chapterId), Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(titleText)
            setContentText(contentText)
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setOngoing(false)
            setAutoCancel(true)
            setProgress(0, 0, false)
        }
    }

    private fun showError(chapterId: Long, message: String, title: String?) {
        context.cancelNotification(Notifications.translationProgressId(chapterId))

        val titleText = titleFor(chapterId, title)
        val contentText = context.stringResource(AYMR.strings.translation_error, message)

        context.notify(Notifications.translationErrorId(chapterId), Notifications.CHANNEL_TRANSLATION_ERROR) {
            setContentTitle(titleText)
            setContentText(contentText)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setOngoing(false)
            setAutoCancel(true)
            setProgress(0, 0, false)
        }
    }
}
