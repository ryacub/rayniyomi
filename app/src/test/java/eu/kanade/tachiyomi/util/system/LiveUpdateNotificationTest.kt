package eu.kanade.tachiyomi.util.system

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiveUpdateNotificationTest {

    @Test
    fun `live update respects SDK permission and cached builder reuse`() {
        val context = mockk<Context>()
        val manager = mockk<NotificationManager>()
        val builder = mockk<NotificationCompat.Builder>()
        every { context.getSystemService(NotificationManager::class.java) } returns manager
        var permitted = true
        every { manager.canPostPromotedNotifications() } answers { permitted }
        val requests = mutableListOf<Boolean>()
        val texts = mutableListOf<String?>()
        every { builder.setRequestPromotedOngoing(any()) } answers {
            requests.add(firstArg())
            builder
        }
        every { builder.setShortCriticalText(any()) } answers {
            texts.add(firstArg())
            builder
        }
        every { builder.setOngoing(any()) } returns builder

        builder.setLiveUpdate(context, "2/19", sdkInt = 35)
        builder.clearLiveUpdate(sdkInt = 35)
        assertEquals(emptyList<Boolean>(), requests)
        verify(exactly = 0) { manager.canPostPromotedNotifications() }

        builder.setLiveUpdate(context, "2/19", sdkInt = 36)
        permitted = false
        builder.setLiveUpdate(context, "3/19", sdkInt = 36)
        builder.clearLiveUpdate(sdkInt = 36)
        permitted = true
        builder.setLiveUpdate(context, null, sdkInt = 36)
        assertEquals(listOf(true, false, false, true), requests)
        assertEquals(listOf("2/19", null, null, null), texts)
    }
}
