package tachiyomi.source.local.entries.manga

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.DisplayMetrics
import com.hippo.unifile.UniFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem

class LocalMangaSourceBrowseTest {

    private val context = mockk<Context>()
    private val fileSystem = mockk<LocalMangaSourceFileSystem>()
    private val now = System.currentTimeMillis()

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        every { context.stringResource(any()) } returns "Local"
        mockkStatic(Resources::class)
        every { Resources.getSystem() } returns mockk { every { displayMetrics } returns DisplayMetrics() }
        mockkObject(ImageUtil)
        every { ImageUtil.isImage(any(), any()) } answers { firstArg<String?>()?.endsWith(".jpg") == true }
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `latest browse reads each folder's modified time once`() = runTest {
        val folders = listOf(folder("A", now - 3), folder("B", now - 1), folder("C", now - 2))
        stubFolders(folders)

        val page = source().getLatestUpdates(1)

        assertEquals(listOf("B", "C", "A"), page.mangas.map { it.title })
        folders.forEach { verify(exactly = 1) { it.lastModified() } }
    }

    @Test
    fun `popular browse finds covers from the folder listing`() = runTest {
        val folders = listOf(folder("A", now), folder("B", now))
        stubFolders(folders)

        val page = source().getPopularManga(1)

        assertEquals(listOf("file:///A/cover.jpg", "file:///B/cover.jpg"), page.mangas.map { it.thumbnail_url })
        verify(exactly = 0) { fileSystem.getMangaDirectory(any()) }
        verify(exactly = 0) { fileSystem.getFilesInMangaDirectory(any()) }
        verify(exactly = 0) { pages.getValue("A").isFile }
    }

    private val pages = mutableMapOf<String, UniFile>()

    private fun source() = LocalMangaSource(context, fileSystem, LocalMangaCoverManager(context, fileSystem))

    private fun stubFolders(folders: List<UniFile>) {
        every { fileSystem.getFilesInBaseDirectory() } returns folders
        folders.forEach { folder ->
            val name = folder.name!!
            every { fileSystem.getMangaDirectory(name) } returns folder
            every { fileSystem.getFilesInMangaDirectory(name) } answers { folder.listFiles()!!.toList() }
        }
    }

    private fun folder(name: String, modified: Long): UniFile {
        val coverUri = mockk<Uri> { every { this@mockk.toString() } returns "file:///$name/cover.jpg" }
        val cover = mockk<UniFile> {
            every { this@mockk.name } returns "cover.jpg"
            every { isFile } returns true
            every { uri } returns coverUri
        }
        val page = mockk<UniFile> {
            every { this@mockk.name } returns "001.png"
            every { isFile } returns true
        }
        pages[name] = page
        return mockk {
            every { this@mockk.name } returns name
            every { isDirectory } returns true
            every { lastModified() } returns modified
            every { listFiles() } returns arrayOf(page, cover)
        }
    }
}
