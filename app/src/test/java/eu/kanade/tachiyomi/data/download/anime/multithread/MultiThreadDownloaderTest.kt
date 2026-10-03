package eu.kanade.tachiyomi.data.download.anime.multithread

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadStateStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Collections

class MultiThreadDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var server: MockWebServer

    // Over 50 MiB, so the downloader makes two chunks.
    private val video = ByteArray(50 * 1024 * 1024 + 4096) { (it % 251).toByte() }.apply {
        MP4_HEADER.copyInto(this)
    }

    private val stateStore = mockk<DownloadStateStore>(relaxed = true) {
        every { loadProgressIfMatching(any(), any()) } returns null
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = RangeDispatcher(video)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `successful multi-thread transfer passes the merger and writes the full file`() = runTest {
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val output = File(tempDir, "episode.mp4")
        val progressUpdates = Collections.synchronizedList(mutableListOf<DownloadProgress>())
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, maxThreadsProvider = { 2 })

        // Virtual time would fire the chunk timeouts at once.
        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = uniFileAt(output),
                onProgress = { progressUpdates += it },
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
        // Chunk threads can append snapshots out of order.
        val finalProgress = progressUpdates.maxBy { it.downloadedBytes }
        finalProgress.chunks.size shouldBe 2
        finalProgress.chunks.all { it.downloadedBytes == it.totalBytes } shouldBe true
        finalProgress.downloadedBytes shouldBe video.size.toLong()
    }

    private fun uniFileAt(file: File): UniFile = mockk {
        every { filePath } returns file.absolutePath
    }

    private class RangeDispatcher(private val body: ByteArray) : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.method == "HEAD") {
                return MockResponse()
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Length", body.size)
            }
            val (start, end) = request.getHeader("Range")!!
                .removePrefix("bytes=")
                .split("-")
                .let { it[0].toInt() to (it[1].toIntOrNull() ?: (body.size - 1)) }
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${body.size}")
                .setBody(Buffer().write(body, start, end - start + 1))
        }
    }

    private companion object {
        const val EPISODE_ID = 42L

        // MP4 "ftyp" box for the signature check.
        val MP4_HEADER =
            byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    }
}
