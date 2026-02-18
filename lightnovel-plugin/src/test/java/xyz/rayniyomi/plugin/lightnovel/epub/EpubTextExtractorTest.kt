package xyz.rayniyomi.plugin.lightnovel.epub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubTextExtractorTest {

    @Test
    fun `parse extracts title and chapter text`() {
        val epub = createTestEpub()

        val content = EpubTextExtractor.parse(epub)

        assertEquals("Test Novel", content.title)
        assertEquals(2, content.chapters.size)
        assertTrue(content.chapters[0].text.contains("First chapter text"))
        assertTrue(content.chapters[1].text.contains("Second chapter text"))
    }

    private fun createTestEpub(): File {
        val epub = File.createTempFile("novel", ".epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            zip.putText("META-INF/container.xml", CONTAINER_XML)
            zip.putText("OEBPS/content.opf", OPF_XML)
            zip.putText("OEBPS/ch1.xhtml", CHAPTER_1)
            zip.putText("OEBPS/ch2.xhtml", CHAPTER_2)
        }
        return epub
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray())
        closeEntry()
    }

    private companion object {
        const val CONTAINER_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """

        const val OPF_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Novel</dc:title>
              </metadata>
              <manifest>
                <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
        """

        const val CHAPTER_1 = """
            <html><head><title>One</title></head><body><p>First chapter text</p></body></html>
        """

        const val CHAPTER_2 = """
            <html><head><title>Two</title></head><body><p>Second chapter text</p></body></html>
        """
    }
}
