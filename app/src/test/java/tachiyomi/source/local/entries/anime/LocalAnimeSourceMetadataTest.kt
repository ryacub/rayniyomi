package tachiyomi.source.local.entries.anime

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.SAnime
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
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import java.io.ByteArrayInputStream

class LocalAnimeSourceMetadataTest {

    private val context = mockk<Context>(moreInterfaces = arrayOf(GraphProvider::class))
    private val fileSystem = mockk<LocalAnimeSourceFileSystem>()
    private val coverManager = mockk<LocalAnimeCoverManager>()
    private val backgroundManager = mockk<LocalAnimeBackgroundManager>()
    private val thumbnailManager = mockk<LocalEpisodeThumbnailManager>()
    private val anime = SAnime.create().apply {
        url = "Show"
        title = "Show"
        thumbnail_url = "file:///cover.jpg"
        background_url = "file:///background.jpg"
    }

    @BeforeEach
    fun setUp() {
        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        every { context.stringResource(any()) } returns "Local"
        val graph = mockk<LocalSourceGraph> { every { json } returns Json { ignoreUnknownKeys = true } }
        every { context.applicationContext } returns context
        every { (context as GraphProvider<*>).graph } returns graph

        val image = mockk<UniFile>()
        val uri = mockk<Uri> { every { this@mockk.toString() } returns "file:///image.jpg" }
        every { image.uri } returns uri
        every { coverManager.find(anime.url) } returns image
        every { backgroundManager.find(anime.url) } returns image
        every { thumbnailManager.find(anime.url, any()) } returns image
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `malformed episodes json keeps the episode list`() = runTest {
        every { fileSystem.getFilesInAnimeDirectory(anime.url) } returns
            listOf(file("episodes.json", TrackedStream("{not json")), episode("Episode 1.mp4"))

        val episodes = source().getEpisodeList(anime)

        assertEquals(listOf("Episode 1"), episodes.map { it.name })
    }

    @Test
    fun `malformed details json keeps the anime details`() = runTest {
        every { fileSystem.getFilesInAnimeDirectory(anime.url) } returns
            listOf(file("details.json", TrackedStream("{not json")))

        val details = source().getAnimeDetails(anime)

        assertEquals("Show", details.title)
    }

    @Test
    fun `episodes json stream closes after parsing`() = runTest {
        val stream = TrackedStream("[]")
        every { fileSystem.getFilesInAnimeDirectory(anime.url) } returns listOf(file("episodes.json", stream))

        source().getEpisodeList(anime)

        assertTrue(stream.closed, "episodes.json stream must close")
    }

    @Test
    fun `details json stream closes after parsing`() = runTest {
        val stream = TrackedStream("""{"title":"Renamed"}""")
        every { fileSystem.getFilesInAnimeDirectory(anime.url) } returns listOf(file("details.json", stream))

        val details = source().getAnimeDetails(anime)

        assertEquals("Renamed", details.title)
        assertTrue(stream.closed, "details.json stream must close")
    }

    private fun source() = LocalAnimeSource(
        context,
        fileSystem,
        coverManager,
        backgroundManager,
        thumbnailManager,
        mockk<LocalAnimeFetchTypeManager>(),
    )

    private fun file(name: String, stream: TrackedStream) = mockk<UniFile> {
        every { this@mockk.name } returns name
        every { openInputStream() } returns stream
    }

    private fun episode(name: String) = mockk<UniFile> {
        every { this@mockk.name } returns name
        every { lastModified() } returns 1L
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
