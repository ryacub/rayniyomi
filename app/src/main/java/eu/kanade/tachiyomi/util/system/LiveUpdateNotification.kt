package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

fun NotificationCompat.Builder.setLiveUpdate(
    context: Context,
    shortText: String?,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationCompat.Builder {
    if (sdkInt < Build.VERSION_CODES.BAKLAVA) return this
    val permitted = context.notificationManager.canPostPromotedNotifications()
    return setRequestPromotedOngoing(permitted)
        .setShortCriticalText(shortText.takeIf { permitted })
}

fun NotificationCompat.Builder.clearLiveUpdate(
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationCompat.Builder {
    if (sdkInt < Build.VERSION_CODES.BAKLAVA) return this
    return setRequestPromotedOngoing(false)
        .setShortCriticalText(null)
        .setOngoing(false)
}
