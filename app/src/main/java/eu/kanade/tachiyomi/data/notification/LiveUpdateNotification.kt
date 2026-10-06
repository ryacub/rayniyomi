package eu.kanade.tachiyomi.data.notification

import android.os.Build
import androidx.core.app.NotificationCompat

internal fun NotificationCompat.Builder.setLiveUpdate(
    permitted: Boolean,
    shortText: String?,
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationCompat.Builder {
    if (sdkInt < Build.VERSION_CODES.BAKLAVA) return this
    return setRequestPromotedOngoing(permitted)
        .setShortCriticalText(shortText.takeIf { permitted })
}

internal fun NotificationCompat.Builder.clearLiveUpdate(
    sdkInt: Int = Build.VERSION.SDK_INT,
): NotificationCompat.Builder {
    if (sdkInt < Build.VERSION_CODES.BAKLAVA) return this
    return setRequestPromotedOngoing(false)
        .setShortCriticalText(null)
        .setOngoing(false)
}
