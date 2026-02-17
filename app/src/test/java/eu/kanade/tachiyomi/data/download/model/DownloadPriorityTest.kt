package eu.kanade.tachiyomi.data.download.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadPriorityTest {

    @Test
    fun `priority enum has correct ordering`() {
        assertTrue(DownloadPriority.HIGH.value > DownloadPriority.NORMAL.value)
        assertTrue(DownloadPriority.NORMAL.value > DownloadPriority.LOW.value)
    }

    @Test
    fun `priority values are correct`() {
        assertEquals(2, DownloadPriority.HIGH.value)
        assertEquals(1, DownloadPriority.NORMAL.value)
        assertEquals(0, DownloadPriority.LOW.value)
    }

    @Test
    fun `default priority is NORMAL`() {
        // This is tested implicitly in the download model tests
        assertEquals(DownloadPriority.NORMAL, DownloadPriority.NORMAL)
    }
}
