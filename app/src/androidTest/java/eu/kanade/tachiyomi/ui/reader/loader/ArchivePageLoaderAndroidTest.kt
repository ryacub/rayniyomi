package eu.kanade.tachiyomi.ui.reader.loader

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.runBlocking
import mihon.core.archive.ArchiveReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArchivePageLoaderAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var archive: File

    @Before
    fun setUp() {
        archive = File.createTempFile("solid-pages", ".7z", instrumentation.targetContext.cacheDir)
        instrumentation.context.assets.open(FIXTURE).use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
    }

    @After
    fun tearDown() {
        archive.delete()
    }

    @Test
    fun solidArchivePagesReadInReverseOrder() = runBlocking {
        val loader = loader()
        try {
            val pages = loader.getPages()

            assertEquals(PAGE_COUNT, pages.size)
            pages.indices.reversed().forEach { index ->
                val bytes = pages[index].read()
                val expected = (FIRST_FILL_BYTE + index).toByte()
                assertEquals(PAGE_SIZE, bytes.size)
                assertTrue("Page $index has the wrong content", bytes.all { it == expected })
            }
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun solidArchivePageReadsDoNotDecodeTheArchiveAgain() = runBlocking {
        val loader = loader()
        try {
            val lastPage = loader.getPages().last()

            val firstRead = measure { lastPage.read() }
            val repeatedReads = measure { repeat(REPEATED_READS) { lastPage.read() } }

            assertTrue(
                "$REPEATED_READS repeated reads took ${repeatedReads}ms, first read took ${firstRead}ms",
                repeatedReads < firstRead * MAX_REPEAT_RATIO,
            )
        } finally {
            loader.recycle()
        }
    }

    @Test
    fun recycledLoaderLeavesNoExtractedPages() = runBlocking {
        val before = cacheEntries()
        val loader = loader()
        loader.getPages().last().read()

        loader.recycle()

        assertEquals(emptySet<String>(), cacheEntries() - before)
    }

    private fun loader(): ArchivePageLoader =
        ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY).use {
            ArchivePageLoader(ArchiveReader(it))
        }

    private fun ReaderPage.read(): ByteArray = stream!!().use { it.readBytes() }

    private inline fun measure(block: () -> Unit): Long {
        val start = SystemClock.elapsedRealtime()
        block()
        return SystemClock.elapsedRealtime() - start
    }

    private fun cacheEntries(): Set<String> =
        File(System.getProperty("java.io.tmpdir"), "archive-cache").list().orEmpty().toSet()

    private companion object {
        // 100 pages of 512 KiB in one LZMA2 block. Page i is filled with byte 32 + i.
        const val FIXTURE = "solid-pages.7z"
        const val PAGE_COUNT = 100
        const val PAGE_SIZE = 512 * 1024
        const val FIRST_FILL_BYTE = 32
        const val REPEATED_READS = 19
        const val MAX_REPEAT_RATIO = 5
    }
}
