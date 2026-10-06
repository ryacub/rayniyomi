package eu.kanade.tachiyomi.data.notification

import androidx.core.app.NotificationCompat
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiveUpdateNotificationTest {

    @Test
    fun `live update respects SDK permission and cached builder reuse`() {
        val builder = mockk<NotificationCompat.Builder>()
        var permitted = true
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

        builder.setLiveUpdate(permitted, "2/19", sdkInt = 35)
        builder.clearLiveUpdate(sdkInt = 35)
        assertEquals(emptyList<Boolean>(), requests)

        builder.setLiveUpdate(permitted, "2/19", sdkInt = 36)
        permitted = false
        builder.setLiveUpdate(permitted, "3/19", sdkInt = 36)
        builder.clearLiveUpdate(sdkInt = 36)
        permitted = true
        builder.setLiveUpdate(permitted, null, sdkInt = 36)
        assertEquals(listOf(true, false, false, true), requests)
        assertEquals(listOf("2/19", null, null, null), texts)
    }
}
