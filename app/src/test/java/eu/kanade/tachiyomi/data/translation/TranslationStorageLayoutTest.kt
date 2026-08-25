package eu.kanade.tachiyomi.data.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TranslationStorageLayoutTest {

    @Test
    fun `sidecar name appends the suffix to the archive file name`() {
        assertEquals("Chapter 1.cbz_translated", TranslationStorageLayout.sidecarDirName("Chapter 1.cbz"))
    }

    @Test
    fun `sidecar names are recognised`() {
        assertTrue(TranslationStorageLayout.isSidecarDirName("Chapter 1.cbz_translated"))
        assertTrue(TranslationStorageLayout.isSidecarDirName(TranslationStorageLayout.sidecarDirName("Vol 2.cbz")))
    }

    @Test
    fun `loose translation folder name without the cbz stem is not a sidecar name`() {
        assertFalse(TranslationStorageLayout.isSidecarDirName("Chapter 1_translated"))
    }

    @Test
    fun `chapter names are not sidecar names`() {
        assertFalse(TranslationStorageLayout.isSidecarDirName("Chapter 1"))
        assertFalse(TranslationStorageLayout.isSidecarDirName("Chapter 1.cbz"))
        assertFalse(TranslationStorageLayout.isSidecarDirName(null))
    }
}
