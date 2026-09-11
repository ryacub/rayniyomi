package eu.kanade.tachiyomi.ui.reader.loader

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mihon.core.archive.ArchiveReader
import mihon.core.archive.EpubReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubPageLoaderAndroidTest {

    @Test
    fun missingEpubImageReferenceIsExcluded() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = File.createTempFile("missing-image", ".epub", context.cacheDir)

        try {
            ZipOutputStream(epub.outputStream()).use { zip ->
                zip.writeEntry("META-INF/container.xml", CONTAINER_XML)
                zip.writeEntry("OEBPS/content.opf", PACKAGE_DOCUMENT)
                zip.writeEntry("OEBPS/page.xhtml", "<img src=\"missing.jpg\" />")
            }

            ParcelFileDescriptor.open(epub, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                EpubReader(ArchiveReader(descriptor)).use { reader ->
                    assertEquals(emptyList<String>(), reader.getImagesFromPages())
                }
            }
        } finally {
            epub.delete()
        }
    }

    private fun ZipOutputStream.writeEntry(name: String, contents: String) {
        putNextEntry(ZipEntry(name))
        write(contents.encodeToByteArray())
        closeEntry()
    }

    private companion object {
        const val CONTAINER_XML = """
            <container>
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" />
              </rootfiles>
            </container>
        """

        const val PACKAGE_DOCUMENT = """
            <package>
              <manifest>
                <item id="page" href="page.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine><itemref idref="page" /></spine>
            </package>
        """
    }
}
