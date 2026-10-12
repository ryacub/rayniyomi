package tachiyomi.source.local.entries.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.di.GraphProvider
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.source.local.di.LocalSourceGraph
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import java.io.ByteArrayInputStream

class LocalMangaSourceTest {

    private val context = mockk<Context>(moreInterfaces = arrayOf(GraphProvider::class))
    private val fileSystem = mockk<LocalMangaSourceFileSystem>()
    private val coverManager = mockk<LocalMangaCoverManager>()
    private val manga = SManga.create().apply {
        url = "Series"
        title = "Series"
        thumbnail_url = "file:///cover.jpg"
    }

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        every { context.stringResource(any()) } returns "Local"
        val graph = mockk<LocalSourceGraph> { every { json } returns Json { ignoreUnknownKeys = true } }
        every { context.applicationContext } returns context
        every { (context as GraphProvider<*>).graph } returns graph
        every { coverManager.find(manga.url) } returns null
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `chapters json stream closes after parsing`() = runTest {
        val stream = TrackedStream("[]")
        every { fileSystem.getFilesInMangaDirectory(manga.url) } returns listOf(file("chapters.json", stream))

        source().getMangaUpdate(manga, emptyList(), fetchDetails = false, fetchChapters = true)

        assertTrue(stream.closed, "chapters.json stream must close")
    }

    @Test
    fun `legacy details json stream closes after parsing`() = runTest {
        val stream = TrackedStream("""{"title":"Renamed"}""")
        val directory = mockk<UniFile> {
            every { listFiles() } returns arrayOf(file("details.json", stream))
            every { createFile(any()) } returns null
        }
        every { fileSystem.getMangaDirectory(manga.url) } returns directory

        val update = source().getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)

        assertEquals("Renamed", update.manga.title)
        assertTrue(stream.closed, "details.json stream must close")
    }

    private fun source() = LocalMangaSource(context, fileSystem, coverManager)

    private fun file(name: String, stream: TrackedStream) = mockk<UniFile> {
        every { this@mockk.name } returns name
        every { isDirectory } returns false
        every { openInputStream() } returns stream
    }
}

private class TrackedStream(text: String) : ByteArrayInputStream(text.encodeToByteArray()) {
    var closed = false
        private set

    override fun close() {
        closed = true
        super.close()
    }
}
