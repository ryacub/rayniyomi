package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import java.util.UUID

internal class LiveUpdateNotificationSession(
    private val context: Context,
    private val notificationId: Int,
) {
    private var token = UUID.randomUUID().toString()
    private var dismissed = false
    private var finished = false
    private var postedTokens = emptyList<String>()

    fun reset() = synchronized(sessions) {
        unregister()
        token = UUID.randomUUID().toString()
        dismissed = false
        finished = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) sessions[token] = this
    }

    fun show(builder: NotificationCompat.Builder) = synchronized(sessions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            context.notify(notificationId, builder.build())
            return@synchronized
        }
        if (dismissed || finished) return@synchronized

        sessions[token] = this
        notificationOwners[notificationId] = token
        postedTokens = sessions.values.filter { it.notificationId == notificationId }.map { it.token }
        builder.setDeleteIntent(NotificationReceiver.dismissLiveUpdatePendingBroadcast(context, token))
        context.notify(notificationId, builder.build())
    }

    fun finish() = synchronized(sessions) {
        unregister()
        finished = true
    }

    private fun unregister() {
        sessions.remove(token)
        if (notificationOwners[notificationId] == token) notificationOwners.remove(notificationId)
    }

    companion object {
        private val sessions = mutableMapOf<String, LiveUpdateNotificationSession>()
        private val notificationOwners = mutableMapOf<Int, String>()

        fun dismiss(token: String?) = synchronized(sessions) {
            val session = sessions[token] ?: return@synchronized
            session.dismissed = true
            if (notificationOwners[session.notificationId] != token) return@synchronized
            session.postedTokens.forEach { sessions[it]?.dismissed = true }
        }
    }
}
