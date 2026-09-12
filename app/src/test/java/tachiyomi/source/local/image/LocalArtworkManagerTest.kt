package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import tachiyomi.source.local.io.manga.LocalMangaSourceFileSystem
import java.io.ByteArrayInputStream
import java.io.IOException

class LocalArtworkManagerTest {

    @Test
    fun `anime cover creation failure closes the input stream`() {
        val fileSystem = mockk<LocalAnimeSourceFileSystem>()
        val directory = mockk<UniFile>()
        every { fileSystem.getAnimeDirectory(any()) } returns directory
        every { fileSystem.getFilesInAnimeDirectory(any()) } returns emptyList()
        every { directory.createFile(any()) } returns null
        val input = CloseTrackingInputStream()

        assertThrows(IOException::class.java) {
            LocalAnimeCoverManager(mockk<Context>(relaxed = true), fileSystem).update(anime(), input)
        }

        assertTrue(input.closed)
    }

    @Test
    fun `anime background creation failure closes the input stream`() {
        val fileSystem = mockk<LocalAnimeSourceFileSystem>()
        val directory = mockk<UniFile>()
        every { fileSystem.getAnimeDirectory(any()) } returns directory
        every { fileSystem.getFilesInAnimeDirectory(any()) } returns emptyList()
        every { directory.createFile(any()) } returns null
        val input = CloseTrackingInputStream()

        assertThrows(IOException::class.java) {
            LocalAnimeBackgroundManager(mockk<Context>(relaxed = true), fileSystem).update(anime(), input)
        }

        assertTrue(input.closed)
    }

    @Test
    fun `episode thumbnail creation failure closes the input stream`() {
        val fileSystem = mockk<LocalAnimeSourceFileSystem>()
        val directory = mockk<UniFile>()
        every { fileSystem.getAnimeDirectory(any()) } returns directory
        every { fileSystem.getFilesInAnimeDirectory(any()) } returns emptyList()
        every { directory.createFile(any()) } returns null
        val input = CloseTrackingInputStream()

        assertThrows(IOException::class.java) {
            LocalEpisodeThumbnailManager(mockk<Context>(relaxed = true), fileSystem).update(
                anime(),
                SEpisode.create().apply { name = "Episode 1" },
                input,
            )
        }

        assertTrue(input.closed)
    }

    @Test
    fun `manga cover creation failure closes the input stream`() {
        val fileSystem = mockk<LocalMangaSourceFileSystem>()
        val directory = mockk<UniFile>()
        every { fileSystem.getMangaDirectory(any()) } returns directory
        every { fileSystem.getFilesInMangaDirectory(any()) } returns emptyList()
        every { directory.createFile(any()) } returns null
        val input = CloseTrackingInputStream()

        assertThrows(IOException::class.java) {
            LocalMangaCoverManager(mockk<Context>(relaxed = true), fileSystem).update(manga(), input)
        }

        assertTrue(input.closed)
    }

    private class CloseTrackingInputStream : ByteArrayInputStream(byteArrayOf()) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    private fun anime() = SAnime.create().apply { url = "Local" }

    private fun manga() = SManga.create().apply { url = "Local" }
}
