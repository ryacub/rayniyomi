package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.manga.model.DownloadedChapterPage
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MangaDownloadManagerBuildPageListTest {

    private lateinit var context: Context
    private lateinit var provider: MangaDownloadProvider
    private lateinit var manager: MangaDownloadManager

    private val manga = Manga.create().copy(id = 1L, title = "Test Manga")
    private val chapter = Chapter.create().copy(id = 11L, name = "Chapter 1")

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        provider = mockk()
        mockkStatic(android.content.res.Resources::class)
        every { android.content.res.Resources.getSystem() } returns mockk {
            every { displayMetrics } returns android.util.DisplayMetrics().apply {
                widthPixels = 1080
                heightPixels = 1920
            }
        }
        // ImageUtil's sniffing fallback needs the native ImageDecoder, which does
        // not exist in JVM tests. Match by extension, its documented first check.
        mockkObject(ImageUtil)
        every { ImageUtil.isImage(any(), any()) } answers {
            val name = firstArg<String?>()
            name != null && name.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
        }
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        every { context.stringResource(any()) } returns NO_PAGES_MESSAGE

        manager = MangaDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = provider,
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = mockk<DownloadPreferences>(relaxed = true),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `loose chapter lists only images sorted by name with working streams`() {
        val firstBytes = byteArrayOf(1, 2, 3)
        val secondBytes = byteArrayOf(4, 5, 6, 7)
        val chapterDir = mockk<UniFile>()
        every { chapterDir.isFile } returns false
        every { chapterDir.listFiles() } returns arrayOf(
            fakeImageFile("002.jpg", secondBytes),
            fakeNonImageFile("notes.txt"),
            fakeImageFile("001.jpg", firstBytes),
        )
        stubChapterDir(chapterDir)

        val pages = collectPages()

        assertEquals(listOf(0, 1), pages.map { it.index })
        assertArrayEquals(firstBytes, pages[0].openStream()!!.readBytes())
        assertArrayEquals(secondBytes, pages[1].openStream()!!.readBytes())
    }

    @Test
    fun `cbz chapter filters non-images and orders entries naturally`() {
        val zipBytes = zipOf(
            "notes.txt" to "decoy".encodeToByteArray(),
            "010.jpg" to byteArrayOf(10),
            "001.jpg" to byteArrayOf(1),
            "9.jpg" to byteArrayOf(9),
        )
        val chapterDir = mockk<UniFile>()
        every { chapterDir.isFile } returns true
        stubChapterDir(chapterDir)
        val archive = FakeZipChapterArchive(zipBytes)
        var openedWith: UniFile? = null
        val archiveOpener: (UniFile) -> ChapterArchive = { file ->
            openedWith = file
            archive
        }
        val archiveManager = MangaDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = provider,
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = mockk<DownloadPreferences>(relaxed = true),
            chapterArchiveOpener = archiveOpener,
        )

        val pages = mutableListOf<DownloadedChapterPage>()
        runBlocking {
            archiveManager.buildPageList(mockk(), manga, chapter) { pages.addAll(it) }
        }

        assertEquals(chapterDir, openedWith)
        assertEquals(listOf(0, 1, 2), pages.map { it.index })
        assertArrayEquals(byteArrayOf(1), pages[0].openStream()!!.readBytes())
        assertArrayEquals(byteArrayOf(9), pages[1].openStream()!!.readBytes())
        assertArrayEquals(byteArrayOf(10), pages[2].openStream()!!.readBytes())
        assertTrue(archive.closed, "Archive must be closed before buildPageList returns")
    }

    @Test
    fun `empty loose chapter throws no pages error`() {
        val chapterDir = mockk<UniFile>()
        every { chapterDir.isFile } returns false
        every { chapterDir.listFiles() } returns emptyArray()
        stubChapterDir(chapterDir)

        val exception = assertThrows<Exception> {
            runBlocking {
                manager.buildPageList(mockk(), manga, chapter) { }
            }
        }

        assertEquals(NO_PAGES_MESSAGE, exception.message)
    }

    private fun collectPages(): List<DownloadedChapterPage> {
        val pages = mutableListOf<DownloadedChapterPage>()
        runBlocking {
            manager.buildPageList(mockk(), manga, chapter) { pages.addAll(it) }
        }
        return pages
    }

    private fun stubChapterDir(dir: UniFile) {
        every { provider.findChapterDir(chapter.name, chapter.scanlator, manga.title, any()) } returns dir
    }

    private fun fakeImageFile(name: String, bytes: ByteArray): UniFile {
        val file = mockk<UniFile>(relaxed = true)
        val uri = mockk<Uri>()
        every { file.name } returns name
        every { file.isFile } returns true
        every { file.uri } returns uri
        every { context.contentResolver.openInputStream(uri) } returns ByteArrayInputStream(bytes)
        return file
    }

    private fun fakeNonImageFile(name: String): UniFile {
        val file = mockk<UniFile>(relaxed = true)
        every { file.name } returns name
        every { file.isFile } returns true
        return file
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** Fake archive over an in-memory zip; stands in for libarchive-backed readers in JVM tests. */
    private class FakeZipChapterArchive(zipBytes: ByteArray) : ChapterArchive {

        private val bytesByName = HashMap<String, ByteArray>()

        var closed = false
            private set

        init {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    bytesByName[entry.name] = zip.readBytes()
                }
            }
        }

        override fun <T> useEntries(block: (Sequence<ChapterArchiveEntry>) -> T): T {
            return block(bytesByName.entries.asSequence().map { ChapterArchiveEntry(it.key, true) })
        }
        override fun getInputStream(entryName: String): InputStream? {
            return bytesByName[entryName]?.let(::ByteArrayInputStream)
        }

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val NO_PAGES_MESSAGE = "No pages found"
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }
}
