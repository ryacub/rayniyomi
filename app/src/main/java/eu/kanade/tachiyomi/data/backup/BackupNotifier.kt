package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.ErrorLogFileResult
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.notification.asShareableErrorLogFile
import eu.kanade.tachiyomi.data.notification.shouldAttachRestoreErrorLogAction
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class BackupNotifier(private val context: Context) {

    private val preferences: SecurityPreferences by injectLazy()

    private val progressNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_BACKUP_RESTORE_PROGRESS,
    ) {
        setLargeIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_ani)
        setAutoCancel(false)
        setOngoing(true)
        setOnlyAlertOnce(true)
    }

    private val completeNotificationBuilder = context.notificationBuilder(
        Notifications.CHANNEL_BACKUP_RESTORE_COMPLETE,
    ) {
        setLargeIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
        setSmallIcon(R.drawable.ic_ani)
        setAutoCancel(false)
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun showBackupProgress(): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.creating_backup))

            setProgress(0, 0, true)
        }

        builder.show(Notifications.ID_BACKUP_PROGRESS)

        return builder
    }

    fun showBackupError(error: String?) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.creating_backup_error))
            setContentText(error)

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showBackupComplete(file: UniFile) {
        context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.backup_created))
            setContentText(file.displayablePath)

            clearActions()
            addAction(
                R.drawable.ic_share_24dp,
                context.stringResource(MR.strings.action_share),
                NotificationReceiver.shareBackupPendingBroadcast(context, file.uri),
            )

            show(Notifications.ID_BACKUP_COMPLETE)
        }
    }

    fun showRestoreProgress(
        content: String = "",
        progress: Int = 0,
        maxAmount: Int = 100,
        sync: Boolean = false,
    ): NotificationCompat.Builder {
        val builder = with(progressNotificationBuilder) {
            val contentTitle = if (sync) {
                context.stringResource(MR.strings.syncing_library)
            } else {
                context.stringResource(MR.strings.restoring_backup)
            }
            setContentTitle(contentTitle)

            if (!preferences.hideNotificationContent().get()) {
                setContentText(content)
            }

            setProgress(maxAmount, progress, false)
            setOnlyAlertOnce(true)

            clearActions()
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                NotificationReceiver.cancelRestorePendingBroadcast(
                    context,
                    Notifications.ID_RESTORE_PROGRESS,
                ),
            )
        }

        builder.show(Notifications.ID_RESTORE_PROGRESS)

        return builder
    }

    fun showRestoreError(error: String?) {
        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        with(completeNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.restoring_backup_error))
            setContentText(error)

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }

    fun showRestoreComplete(
        time: Long,
        errorCount: Int,
        errorLogFileResult: ErrorLogFileResult,
        sync: Boolean,
    ) {
        val contentTitle = if (sync) {
            context.stringResource(MR.strings.library_sync_complete)
        } else {
            context.stringResource(MR.strings.restore_completed)
        }

        context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)

        val timeString = context.stringResource(
            MR.strings.restore_duration,
            TimeUnit.MILLISECONDS.toMinutes(time),
            TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(
                TimeUnit.MILLISECONDS.toMinutes(time),
            ),
        )

        with(completeNotificationBuilder) {
            setContentTitle(contentTitle)
            setContentText(
                context.resources.getQuantityString(
                    R.plurals.restore_completed_message,
                    errorCount,
                    timeString,
                    errorCount,
                ),
            )

            clearActions()
            val shareableErrorLogFile = asShareableErrorLogFile(errorLogFileResult)?.takeIf {
                shouldAttachRestoreErrorLogAction(errorCount, errorLogFileResult)
            }
            if (shareableErrorLogFile != null) {
                val uri = shareableErrorLogFile.getUriCompat(context)

                val errorLogIntent = NotificationReceiver.openErrorLogPendingActivity(context, uri)
                setContentIntent(errorLogIntent)
                addAction(
                    R.drawable.ic_folder_24dp,
                    context.stringResource(MR.strings.action_show_errors),
                    errorLogIntent,
                )
            } else if (errorLogFileResult is ErrorLogFileResult.Failed) {
                logcat(LogPriority.WARN, errorLogFileResult.cause) {
                    "Failed to create backup restore error file; skipping error log action"
                }
            }

            show(Notifications.ID_RESTORE_COMPLETE)
        }
    }
}
