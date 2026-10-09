package tachiyomi.source.local.entries.anime

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFprobeSession
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class LocalAnimeSourceTest {

    private val context = mockk<Context>()
    private val fileSystem = mockk<LocalAnimeSourceFileSystem>()
    private val coverManager = mockk<LocalAnimeCoverManager>()
    private val backgroundManager = mockk<LocalAnimeBackgroundManager>()
    private val thumbnailManager = mockk<LocalEpisodeThumbnailManager>()
    private val anime = SAnime.create().apply {
        url = "Local"
        title = "R1148-${UUID.randomUUID()}"
        thumbnail_url = "file:///cover.jpg"
        background_url = "file:///background.jpg"
    }
    private val thumbnail = mockk<UniFile>()
    private val outputFiles = mutableListOf<File>()
    private var savedThumbnail: UniFile? = null

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        every { context.stringResource(any()) } returns "Local"
        mockkStatic(FFprobeKit::class, FFmpegKit::class)

        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.toString() } returns "file:///Episode%201-thumbnail.jpg"
        every { thumbnail.uri } returns uri
        val episodeFile = mockk<UniFile>()
        every { episodeFile.name } returns "Episode 1.mp4"
        every { episodeFile.lastModified() } returns 1L
        every { episodeFile.uri } returns uri
        every { episodeFile.filePath } returns "/fixture/Episode 1.mp4"
        mockkStatic("eu.kanade.tachiyomi.util.storage.FFmpegUtilsKt")
        every { episodeFile.toFFmpegString(context) } returnsMany listOf("saf:1", "saf:2")
        val directory = mockk<UniFile>()
        every { directory.findFile("Episode 1.mp4") } returns episodeFile
        every { fileSystem.getFilesInAnimeDirectory(anime.url) } returns listOf(episodeFile)
        every { fileSystem.getAnimeDirectory(anime.url) } returns directory
        every { coverManager.find(anime.url) } returns thumbnail
        every { backgroundManager.find(anime.url) } returns thumbnail
        every { thumbnailManager.find(anime.url, "Episode 1-thumbnail") } answers { savedThumbnail }
        every { thumbnailManager.update(anime, any(), any()) } answers {
            thirdArg<InputStream>().use { it.readBytes() }
            savedThumbnail = thumbnail
            secondArg<SEpisode>().preview_url = uri.toString()
            thumbnail
        }

        val probe = mockk<FFprobeSession>()
        every { probe.allLogsAsString } returns "20"
        every { FFprobeKit.execute(any()) } returns probe
        every { FFmpegKit.execute(any()) } answers {
            val path = firstArg<String>().substringBeforeLast('"').substringAfterLast('"')
            File(path).also {
                outputFiles.add(it)
                it.writeBytes(byteArrayOf(1, 2, 3))
            }
            mockk<FFmpegSession>()
        }
    }

    @AfterEach
    fun tearDown() {
        outputFiles.forEach { it.delete() }
        temporaryFiles().forEach { it.delete() }
        unmockkAll()
    }

    @Test
    fun `second episode refresh reuses the saved thumbnail and removes extraction output`() = runTest {
        val source = source()

        val first = source.getEpisodeList(anime).single()
        val second = source.getEpisodeList(anime).single()

        assertEquals("file:///Episode%201-thumbnail.jpg", first.preview_url)
        assertEquals(first.preview_url, second.preview_url)
        verify(exactly = 1) { FFprobeKit.execute(any()) }
        verify(exactly = 1) { FFmpegKit.execute(any()) }
        verify { FFprobeKit.execute(match { it.contains("saf:1") }) }
        verify { FFmpegKit.execute(match { it.contains("saf:2") }) }
        assertEquals(1, outputFiles.size)
        assertFalse(outputFiles.single().exists(), "Extraction must delete its temporary output")
    }

    @Test
    fun `failed video probe removes its temporary file`() = runTest {
        every { FFprobeKit.execute(any()) } throws IOException("Video cannot be probed")

        source().getEpisodeList(anime)

        verify(exactly = 1) { FFprobeKit.execute(any()) }
        assertTrue(temporaryFiles().isEmpty(), "A failed extraction must delete its temporary file")
    }

    private fun source() = LocalAnimeSource(
        context,
        fileSystem,
        coverManager,
        backgroundManager,
        thumbnailManager,
        mockk<LocalAnimeFetchTypeManager>(),
    )

    private fun temporaryFiles(): List<File> = File(System.getProperty("java.io.tmpdir"))
        .listFiles()
        .orEmpty()
        .filter { it.name.endsWith("${anime.title}Episode 1thumbnail.jpg") }
}
