package eu.kanade.tachiyomi.data.download.anime.multithread

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkProgress
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkSizeCalculator
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadStateStore
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
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

    @Test
    fun `resume downloads again a chunk saved as complete whose file is gone`() = runTest {
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }

        resumeFromCompleteChunks(chunkDir)
    }

    @Test
    fun `resume downloads again a chunk saved as complete whose file is truncated`() = runTest {
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        File(chunkDir, "chunk_${EPISODE_ID}_0.tmp").writeBytes(video.copyOf(1024))

        resumeFromCompleteChunks(chunkDir)
    }

    @Test
    fun `a cancelled download stops writing chunk files before it completes`() = runBlocking<Unit> {
        server.dispatcher = RangeDispatcher(video, bytesPer20Millis = 1024 * 1024)
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val firstProgress = CountDownLatch(1)
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, maxThreadsProvider = { 2 })

        val download = launch(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(File(tempDir, "episode.mp4")),
                onProgress = { firstProgress.countDown() },
            )
        }
        firstProgress.await(10, TimeUnit.SECONDS) shouldBe true
        download.cancelAndJoin()
        val bytesAtCompletion = chunkDir.listFiles().orEmpty().sumOf { it.length() }

        delay(500)

        chunkDir.listFiles().orEmpty().sumOf { it.length() } shouldBe bytesAtCompletion
    }

    @Test
    fun `a cancelled download stops its chunk reads at once`() = runBlocking<Unit> {
        // About 8 seconds for each chunk, so an unstopped read fills the chunk files after the cancel.
        server.dispatcher = RangeDispatcher(video, bytesPer20Millis = 64 * 1024)
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val firstProgress = CountDownLatch(1)
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, maxThreadsProvider = { 2 })

        val download = launch(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(File(tempDir, "episode.mp4")),
                onProgress = { firstProgress.countDown() },
            )
        }
        firstProgress.await(10, TimeUnit.SECONDS) shouldBe true
        download.cancelAndJoin()

        chunkDir.listFiles().orEmpty().sumOf { it.length() } shouldBeLessThan 8L * 1024 * 1024
    }

    @Test
    fun `a chunk slower than the client call timeout still completes`() = runBlocking<Unit> {
        // About 2.5 seconds for each chunk, longer than the call timeout.
        server.dispatcher = RangeDispatcher(video, bytesPer20Millis = 200 * 1024)
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val output = File(tempDir, "episode.mp4")
        val client = OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).build()
        val downloader = MultiThreadDownloader(client, stateStore, maxThreadsProvider = { 2 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
    }

    @Test
    fun `a chunk with no response fails as a timeout`() = runBlocking<Unit> {
        val ranges = RangeDispatcher(video)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.method == "HEAD") {
                    ranges.dispatch(request)
                } else {
                    MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                }
        }
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val client = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        val downloader = MultiThreadDownloader(client, stateStore, maxThreadsProvider = { 2 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(File(tempDir, "episode.mp4")),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Error>()
        result.error.shouldBeInstanceOf<DownloadError.Timeout>()
    }

    @Test
    fun `a chunk retry requests only the bytes that are not on disk`() = runBlocking<Unit> {
        val ranges = RangeDispatcher(video, disconnectFirstBody = true)
        server.dispatcher = ranges
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val output = File(tempDir, "episode.mp4")
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, maxThreadsProvider = { 1 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
        ranges.requestedRanges.size shouldBe 2
        ranges.requestedRanges[1].rangeStart() shouldBeGreaterThan 0L
    }

    @Test
    fun `a resumed download requests only the bytes that are not on disk`() = runBlocking<Unit> {
        server.dispatcher = RangeDispatcher(video, bytesPer20Millis = 64 * 1024)
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val output = File(tempDir, "episode.mp4")
        val videoUrl = server.url("/episode.mp4").toString()
        val saved = slot<DownloadProgress>()
        val pausingStore = mockk<DownloadStateStore>(relaxed = true) {
            every { loadProgressIfMatching(any(), any()) } returns null
            every { saveProgress(capture(saved)) } just Runs
        }
        val someBytesWritten = CountDownLatch(1)
        val download = launch(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), pausingStore, maxThreadsProvider = { 2 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
                onProgress = { if (it.downloadedBytes > 2L * 1024 * 1024) someBytesWritten.countDown() },
            )
        }
        someBytesWritten.await(10, TimeUnit.SECONDS) shouldBe true
        download.cancelAndJoin()
        val chunkStarts = saved.captured.chunks.map { it.startByte }

        val ranges = RangeDispatcher(video)
        server.dispatcher = ranges
        val resumingStore = mockk<DownloadStateStore>(relaxed = true) {
            every { loadProgressIfMatching(EPISODE_ID, videoUrl) } returns saved.captured
        }
        val result = withContext(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), resumingStore, maxThreadsProvider = { 2 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
        ranges.requestedRanges.map { it.rangeStart() }.filter { it in chunkStarts } shouldBe emptyList()
    }

    @Test
    fun `a new download writes over a chunk file left by another download`() = runTest {
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        File(chunkDir, "chunk_${EPISODE_ID}_0.tmp").writeBytes(ByteArray(1024 * 1024) { 7 })
        val output = File(tempDir, "episode.mp4")
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, maxThreadsProvider = { 2 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
    }

    private suspend fun resumeFromCompleteChunks(chunkDir: File) {
        val output = File(tempDir, "episode.mp4")
        val videoUrl = server.url("/episode.mp4").toString()
        val savedProgress = DownloadProgress(
            episodeId = EPISODE_ID,
            videoUrl = videoUrl,
            totalBytes = video.size.toLong(),
            downloadedBytes = video.size.toLong(),
            chunks = ChunkSizeCalculator.calculateChunks(video.size.toLong(), 2).mapIndexed { index, range ->
                ChunkProgress(
                    index = index,
                    startByte = range.startByte,
                    endByte = range.endByte,
                    downloadedBytes = range.endByte - range.startByte + 1,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_${EPISODE_ID}_$index.tmp",
                )
            },
            status = DownloadProgress.Status.ERROR,
        )
        val resumingStore = mockk<DownloadStateStore>(relaxed = true) {
            every { loadProgressIfMatching(EPISODE_ID, videoUrl) } returns savedProgress
        }
        val downloader = MultiThreadDownloader(OkHttpClient(), resumingStore, maxThreadsProvider = { 2 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(chunkDir),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
    }

    private fun String.rangeStart(): Long = removePrefix("bytes=").substringBefore('-').toLong()

    private fun uniFileAt(file: File): UniFile = mockk {
        every { filePath } returns file.absolutePath
    }

    // A SAF document has no usable file path, so the app can only write it through its stream.
    private fun documentAt(file: File): UniFile = mockk {
        every { filePath } returns null
        every { openOutputStream() } answers { file.outputStream() }
        every { delete() } answers { file.delete() }
    }

    private class RangeDispatcher(
        private val body: ByteArray,
        private val bytesPer20Millis: Long? = null,
        disconnectFirstBody: Boolean = false,
    ) : Dispatcher() {
        val requestedRanges: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val disconnectNext = AtomicBoolean(disconnectFirstBody)

        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.method == "HEAD") {
                return MockResponse()
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Length", body.size)
            }
            // A GET without a range gets the 200 that the downloader rejects as InvalidRange.
            val range = request.getHeader("Range") ?: return MockResponse().setBody(Buffer().write(body))
            requestedRanges += range
            val (start, end) = range
                .removePrefix("bytes=")
                .split("-")
                .let { it[0].toInt() to (it[1].toIntOrNull() ?: (body.size - 1)) }
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $start-$end/${body.size}")
                .setBody(Buffer().write(body, start, end - start + 1))
                .apply { bytesPer20Millis?.let { throttleBody(it, 20, TimeUnit.MILLISECONDS) } }
                .apply {
                    if (disconnectNext.compareAndSet(true, false)) {
                        setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    }
                }
        }
    }

    private companion object {
        const val EPISODE_ID = 42L

        // MP4 "ftyp" box for the signature check.
        val MP4_HEADER =
            byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    }
}
