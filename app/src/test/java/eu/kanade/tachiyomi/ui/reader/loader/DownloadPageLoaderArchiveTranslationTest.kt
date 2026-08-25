package eu.kanade.tachiyomi.ui.reader.loader

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class DownloadPageLoaderArchiveTranslationTest {

    private val translatedFile = mockk<UniFile>()
    private val originalBytes = byteArrayOf(1, 1, 1)
    private val translatedBytes = byteArrayOf(9, 9, 9, 9)

    /** Archive with two pages; only page index 0 is translated. */
    private fun archivePages(): List<ReaderPage> = listOf(
        ReaderPage(0) { ByteArrayInputStream(originalBytes) },
        ReaderPage(1) { ByteArrayInputStream(originalBytes) },
    )

    private fun lookupForTranslatedFirstPage(): (Int) -> UniFile? = { index ->
        if (index == 0) translatedFile else null
    }

    @Test
    fun `translated page substitutes the stored file when the preference is on`() {
        val pages = archivePages()

        val result = substituteTranslatedPages(
            pages,
            showTranslated = true,
            lookup = lookupForTranslatedFirstPage(),
            open = { ByteArrayInputStream(translatedBytes) },
        )

        assertNotSame(pages[0], result[0])
        assertEquals(0, result[0].index)
        assertEquals(translatedBytes.toList(), result[0].stream!!.invoke().readBytes().toList())
    }

    @Test
    fun `pages pass through unchanged when the preference is off`() {
        var lookups = 0
        val pages = archivePages()

        val result = substituteTranslatedPages(
            pages,
            showTranslated = false,
            lookup = { _ ->
                lookups += 1
                translatedFile
            },
            open = { ByteArrayInputStream(translatedBytes) },
        )

        assertEquals(0, lookups)
        assertSame(pages[0], result[0])
        assertSame(pages[1], result[1])
    }

    @Test
    fun `untranslated pages in a partly translated archive keep the original stream`() {
        val pages = archivePages()

        val result = substituteTranslatedPages(
            pages,
            showTranslated = true,
            lookup = lookupForTranslatedFirstPage(),
            open = { ByteArrayInputStream(translatedBytes) },
        )

        assertSame(pages[1], result[1])
        assertEquals(originalBytes.toList(), result[1].stream!!.invoke().readBytes().toList())
    }

    @Test
    fun `a translated file that fails to open falls back to the original stream`() {
        val result = substituteTranslatedPages(
            archivePages(),
            showTranslated = true,
            lookup = lookupForTranslatedFirstPage(),
            open = { throw IllegalStateException("file vanished") },
        )

        assertEquals(originalBytes.toList(), result[0].stream!!.invoke().readBytes().toList())
    }
}
