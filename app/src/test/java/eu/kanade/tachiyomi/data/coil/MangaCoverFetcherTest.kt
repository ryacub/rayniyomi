package eu.kanade.tachiyomi.data.coil

import coil3.Extras
import coil3.fetch.SourceFetchResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MangaCoverFetcherTest {

    @TempDir
    lateinit var cacheDir: File

    @Test
    fun `local cover file URI resolves a folder name with spaces`() = runTest {
        val cover = File(cacheDir, "Test Manga Cobalt/cover.png").apply {
            parentFile.mkdirs()
            writeText("cover")
        }
        val fetcher = MangaCoverFetcher(
            url = "file://${cover.toURI().rawPath}",
            isLibraryManga = false,
            options = mockk { every { extras } returns Extras.EMPTY },
            coverFileLazy = lazyOf(null),
            customCoverFileLazy = lazyOf(File(cacheDir, "custom-cover.png")),
            diskCacheKeyLazy = lazyOf("local-cover"),
            sourceLazy = lazyOf(null),
            callFactoryLazy = lazy { error("Local covers do not use the network") },
            imageLoader = mockk(),
        )

        val result = fetcher.fetch() as SourceFetchResult

        result.source.use {
            assertEquals(cover, it.file().toFile())
            assertEquals("cover", it.source().readUtf8())
        }
    }
}
