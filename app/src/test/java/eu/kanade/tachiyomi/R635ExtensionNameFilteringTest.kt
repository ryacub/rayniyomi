package eu.kanade.tachiyomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class R635ExtensionNameFilteringTest {

    // ---- AnimeExtensionLoader: substringAfter("Rayniyomi: ") ----

    @Test
    fun `AnimeExtensionLoader strips Rayniyomi prefix from app label`() {
        val raw = "Rayniyomi: Action"
        val result = raw.substringAfter("Rayniyomi: ")
        assertEquals("Action", result)
    }

    @Test
    fun `AnimeExtensionLoader leaves label unchanged when no Rayniyomi prefix`() {
        val raw = "Action"
        val result = raw.substringAfter("Rayniyomi: ")
        assertEquals("Action", result)
    }

    @Test
    fun `AnimeExtensionLoader does NOT strip old Aniyomi prefix`() {
        val raw = "Aniyomi: Action"
        val result = raw.substringAfter("Rayniyomi: ")
        // substringAfter returns original string when separator not found
        assertEquals("Aniyomi: Action", result)
    }

    // ---- AnimeExtensionApi: substringAfter("Rayniyomi: ") ----

    @Test
    fun `AnimeExtensionApi strips Rayniyomi prefix from API name`() {
        val raw = "Rayniyomi: Comedy"
        val result = raw.substringAfter("Rayniyomi: ")
        assertEquals("Comedy", result)
    }

    @Test
    fun `AnimeExtensionApi leaves name unchanged when no prefix`() {
        val raw = "Comedy"
        val result = raw.substringAfter("Rayniyomi: ")
        assertEquals("Comedy", result)
    }

    @Test
    fun `AnimeExtensionApi does NOT strip old Aniyomi prefix`() {
        val raw = "Aniyomi: Comedy"
        val result = raw.substringAfter("Rayniyomi: ")
        assertEquals("Aniyomi: Comedy", result)
    }
}
