package eu.kanade.tachiyomi.ui.reader.loader

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.runBlocking
import me.zhanghai.android.libarchive.Archive
import me.zhanghai.android.libarchive.ArchiveEntry
import mihon.core.archive.ArchiveReader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class ArchivePageLoaderAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var archive: File

    @Before
    fun setUp() {
        archive = File.createTempFile("solid-pages", ".7z", instrumentation.targetContext.cacheDir)
        writeSolidArchive(archive)
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

        val deadline = SystemClock.elapsedRealtime() + CLEANUP_TIMEOUT_MS
        while ((cacheEntries() - before).isNotEmpty() && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(50)
        }
        assertEquals(emptySet<String>(), cacheEntries() - before)
    }

    private fun loader(): ArchivePageLoader =
        ParcelFileDescriptor.open(archive, ParcelFileDescriptor.MODE_READ_ONLY).use {
            ArchivePageLoader(ArchiveReader(it))
        }

    // libarchive's 7z writer compresses every entry into one block, so the archive is solid.
    private fun writeSolidArchive(file: File) {
        val archive = Archive.writeNew()
        try {
            Archive.writeSetFormat7zip(archive)
            Archive.writeSetFormatOption(archive, null, "compression-level".toByteArray(), "1".toByteArray())
            Archive.writeOpenFileName(archive, file.path.toByteArray())
            repeat(PAGE_COUNT) { index ->
                val entry = ArchiveEntry.new1()
                try {
                    ArchiveEntry.setPathnameUtf8(entry, "page%03d.jpg".format(index))
                    ArchiveEntry.setFiletype(entry, ArchiveEntry.AE_IFREG)
                    ArchiveEntry.setPerm(entry, "644".toInt(8))
                    ArchiveEntry.setSize(entry, PAGE_SIZE.toLong())
                    Archive.writeHeader(archive, entry)
                } finally {
                    ArchiveEntry.free(entry)
                }
                val page = ByteBuffer.allocateDirect(PAGE_SIZE)
                repeat(PAGE_SIZE) { page.put((FIRST_FILL_BYTE + index).toByte()) }
                page.flip()
                Archive.writeData(archive, page)
            }
            Archive.writeClose(archive)
        } finally {
            Archive.writeFree(archive)
        }
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
        const val PAGE_COUNT = 40
        const val PAGE_SIZE = 256 * 1024
        const val FIRST_FILL_BYTE = 32
        const val REPEATED_READS = 19
        const val MAX_REPEAT_RATIO = 5
        const val CLEANUP_TIMEOUT_MS = 5_000L
    }
}
