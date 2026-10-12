package tachiyomi.source.local.entries.anime

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.util.DisplayMetrics
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
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
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem

class LocalAnimeSourceBrowseTest {

    private val context = mockk<Context>()
    private val fileSystem = mockk<LocalAnimeSourceFileSystem>()
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

        assertEquals(listOf("B", "C", "A"), page.animes.map { it.title })
        folders.forEach { verify(exactly = 1) { it.lastModified() } }
    }

    @Test
    fun `popular browse reads artwork and fetch type from the folder listing`() = runTest {
        val folders = listOf(folder("A", now), folder("B", now))
        stubFolders(folders)

        val animes = source().getPopularAnime(1).animes

        assertEquals(listOf("file:///A/cover.jpg", "file:///B/cover.jpg"), animes.map { it.thumbnail_url })
        assertEquals(listOf("file:///A/background.jpg", "file:///B/background.jpg"), animes.map { it.background_url })
        assertEquals(listOf(FetchType.Episodes, FetchType.Episodes), animes.map { it.fetch_type })
        verify(exactly = 0) { fileSystem.getAnimeDirectory(any()) }
        verify(exactly = 0) { fileSystem.getFilesInAnimeDirectory(any()) }
    }

    @Test
    fun `season list reads each season folder from the show listing`() = runTest {
        val seasons = listOf(folder("S1", now), folder("S2", now))
        val show = SAnime.create().apply {
            url = "Show"
            title = "Show"
        }
        every { fileSystem.getFilesInAnimeDirectory(any()) } answers {
            when (val url = firstArg<String>()) {
                "Show" -> seasons
                else -> seasons.single { "Show/${it.name}" == url }.listFiles()!!.toList()
            }
        }

        val result = source().getSeasonList(show)

        assertEquals(listOf("Show/S1", "Show/S2"), result.map { it.url })
        assertEquals(listOf("file:///S1/cover.jpg", "file:///S2/cover.jpg"), result.map { it.thumbnail_url })
        verify(exactly = 1) { fileSystem.getFilesInAnimeDirectory(any()) }
    }

    private fun source() = LocalAnimeSource(
        context,
        fileSystem,
        LocalAnimeCoverManager(context, fileSystem),
        LocalAnimeBackgroundManager(context, fileSystem),
        mockk(),
        LocalAnimeFetchTypeManager(context, fileSystem),
    )

    private fun stubFolders(folders: List<UniFile>) {
        every { fileSystem.getFilesInBaseDirectory() } returns folders
        folders.forEach { folder ->
            val name = folder.name!!
            every { fileSystem.getAnimeDirectory(name) } returns folder
            every { fileSystem.getFilesInAnimeDirectory(name) } answers { folder.listFiles()!!.toList() }
        }
    }

    private fun folder(name: String, modified: Long): UniFile {
        val files = listOf("cover.jpg", "background.jpg").map { fileName ->
            val fileUri = mockk<Uri> { every { this@mockk.toString() } returns "file:///$name/$fileName" }
            mockk<UniFile> {
                every { this@mockk.name } returns fileName
                every { isFile } returns true
                every { isDirectory } returns false
                every { uri } returns fileUri
            }
        }
        val episode = mockk<UniFile> {
            every { this@mockk.name } returns "Episode 1.mp4"
            every { isFile } returns true
            every { isDirectory } returns false
        }
        return mockk {
            every { this@mockk.name } returns name
            every { isDirectory } returns true
            every { lastModified() } returns modified
            every { listFiles() } returns (files + episode).toTypedArray()
        }
    }
}
