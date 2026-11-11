package eu.kanade.tachiyomi.data.download.anime.multithread

import android.content.Context
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import java.util.concurrent.atomic.AtomicReference

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
        every { checkpoint(any()) } returns true
    }

    @BeforeEach
    fun setUp() {
        every { stateStore.chunkDirectory } returns File(tempDir, "chunks")
        server = MockWebServer()
        server.dispatcher = RangeDispatcher(video)
        server.start()
    }

    private val appCache by lazy { File(tempDir, "cache") }
    private val storageFolder by lazy { File(tempDir, "storage").apply { mkdirs() } }
    private val lockedFolders = mutableListOf<File>()

    @AfterEach
    fun tearDown() {
        lockedFolders.forEach { it.setWritable(true) }
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
                tmpDir = uniFileAt(storageFolder),
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
    fun `a fresh process can load the episode progress before the first chunk request`() = runTest {
        val context = mockk<Context> {
            every { cacheDir } returns tempDir
        }
        val ranges = RangeDispatcher(video)
        val videoUrl = server.url("/episode.mp4").toString()
        val foundProgressOnDisk = AtomicBoolean()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.getHeader("Range") != null) {
                    val progress = DownloadStateStore(context).loadProgress(EPISODE_ID)
                    foundProgressOnDisk.set(progress?.videoUrl == videoUrl && progress.chunks.isNotEmpty())
                }
                return ranges.dispatch(request)
            }
        }
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val downloader = MultiThreadDownloader(OkHttpClient(), DownloadStateStore(context), maxThreadsProvider = { 1 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(storageFolder),
                outputFile = documentAt(File(tempDir, "episode.mp4")),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        foundProgressOnDisk.get() shouldBe true
    }

    @Test
    fun `a legacy checkpoint cannot resume from unverified bytes at the same URL`() = runTest {
        val videoUrl = server.url("/episode.mp4").toString()
        val stalePrefix = video.copyOf(1024).apply { this[lastIndex] = 99 }
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val chunkName = "chunk_${EPISODE_ID}_0.tmp"
        File(chunkDir, chunkName).writeBytes(stalePrefix)
        val progress = DownloadProgress(
            episodeId = EPISODE_ID,
            videoUrl = videoUrl,
            totalBytes = video.size.toLong(),
            chunks = listOf(ChunkProgress(0, 0, video.lastIndex.toLong(), tempFileName = chunkName)),
        )
        every { stateStore.loadProgressIfMatching(EPISODE_ID, videoUrl) } returns progress
        val output = File(tempDir, "episode.mp4")
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, maxThreadsProvider = { 1 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(EPISODE_ID, videoUrl, null, uniFileAt(storageFolder), documentAt(output))
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
    }

    @Test
    fun `a reused ETag on a different redirect target cannot validate a staged prefix`() = runTest {
        val videoUrl = server.url("/episode.mp4").toString()
        val replacement = video.copyOf().apply { this[1023] = 99 }
        val oldRanges = RangeDispatcher(video, bytesPer20Millis = 64 * 1024)
        val newRanges = RangeDispatcher(replacement)
        val target = AtomicReference("/original.mp4")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/episode.mp4" -> MockResponse().setResponseCode(302).setHeader("Location", target.get())
                "/original.mp4" -> oldRanges.dispatch(request)
                else -> newRanges.dispatch(request)
            }
        }
        val cache = mockk<Context> { every { cacheDir } returns tempDir }
        val chunkDir = File(tempDir, "chunks").apply { mkdirs() }
        val output = File(tempDir, "episode.mp4")
        val downloader = MultiThreadDownloader(OkHttpClient(), DownloadStateStore(cache), maxThreadsProvider = { 1 })
        val checkpoint = AtomicReference<DownloadProgress>()
        val storeWithCheckpoint = mockk<DownloadStateStore>(relaxed = true) {
            every { chunkDirectory } returns File(tempDir, "chunks")
            every { loadProgressIfMatching(any(), any()) } answers { checkpoint.get() }
            every { checkpoint(any()) } answers {
                checkpoint.set(firstArg())
                true
            }
            every { saveProgress(any()) } answers { checkpoint.set(firstArg()) }
        }
        val firstProgress = CompletableDeferred<Unit>()
        val download = launch(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), storeWithCheckpoint, maxThreadsProvider = { 1 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(storageFolder),
                outputFile = documentAt(output),
            ) {
                firstProgress.complete(Unit)
            }
        }
        firstProgress.await()
        download.cancelAndJoin()
        val saved = checkNotNull(checkpoint.get())
        DownloadStateStore(cache).saveProgress(saved)
        target.set("/replacement.mp4")

        val result = withContext(Dispatchers.IO) {
            downloader.download(EPISODE_ID, videoUrl, null, uniFileAt(storageFolder), documentAt(output))
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(replacement) shouldBe true
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
                tmpDir = uniFileAt(storageFolder),
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
                tmpDir = uniFileAt(storageFolder),
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
                tmpDir = uniFileAt(storageFolder),
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
                tmpDir = uniFileAt(storageFolder),
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
                tmpDir = uniFileAt(storageFolder),
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
            every { chunkDirectory } returns File(tempDir, "chunks")
            every { loadProgressIfMatching(any(), any()) } returns null
            every { checkpoint(any()) } returns true
            every { saveProgress(capture(saved)) } just Runs
        }
        val someBytesWritten = CountDownLatch(1)
        val download = launch(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), pausingStore, maxThreadsProvider = { 2 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(storageFolder),
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
            every { chunkDirectory } returns File(tempDir, "chunks")
            every { loadProgressIfMatching(EPISODE_ID, videoUrl) } returns saved.captured
        }
        val result = withContext(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), resumingStore, maxThreadsProvider = { 2 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(storageFolder),
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
                tmpDir = uniFileAt(storageFolder),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
    }

    @Test
    fun `a download into a folder without file write access completes and keeps no chunk files`() = runTest {
        val (folder, output) = folderWithoutFileWriteAccess()
        val downloader = MultiThreadDownloader(OkHttpClient(), appStore(), maxThreadsProvider = { 2 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = server.url("/episode.mp4").toString(),
                headers = null,
                tmpDir = uniFileAt(folder),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
        folder.list().orEmpty().toList() shouldBe listOf(output.name)
        chunkFilesIn(appCache) shouldBe emptyList()
    }

    @Test
    fun `an interrupted download in a folder without file write access resumes after a restart`() =
        runBlocking<Unit> {
            val (folder, output) = folderWithoutFileWriteAccess()
            val videoUrl = server.url("/episode.mp4").toString()
            interruptAfterTwoMebibytes(folder, output, videoUrl)
            val chunkStarts = appStore().loadProgress(EPISODE_ID)!!.chunks.map { it.startByte }

            val ranges = RangeDispatcher(video)
            server.dispatcher = ranges
            val result = withContext(Dispatchers.IO) {
                MultiThreadDownloader(OkHttpClient(), appStore(), maxThreadsProvider = { 2 }).download(
                    episodeId = EPISODE_ID,
                    videoUrl = videoUrl,
                    headers = null,
                    tmpDir = uniFileAt(folder),
                    outputFile = documentAt(output),
                )
            }

            result.shouldBeInstanceOf<DownloadResult.Success>()
            output.readBytes().contentEquals(video) shouldBe true
            ranges.requestedRanges.map { it.rangeStart() }.filter { it in chunkStarts } shouldBe emptyList()
        }

    @Test
    fun `deleting the progress of an interrupted download removes its staged chunks`() = runBlocking<Unit> {
        val (folder, output) = folderWithoutFileWriteAccess()
        interruptAfterTwoMebibytes(folder, output, server.url("/episode.mp4").toString())
        chunkFilesIn(appCache).isNotEmpty() shouldBe true

        appStore().deleteProgress(EPISODE_ID)

        chunkFilesIn(appCache) shouldBe emptyList()
    }

    @Test
    fun `a resumed download removes chunk files that an older version left in the storage folder`() = runTest {
        val folder = File(tempDir, "episode_tmp").apply { mkdirs() }
        val legacyChunk = File(folder, "chunk_${EPISODE_ID}_0.tmp").apply { writeBytes(video.copyOf(1024)) }
        // An older version with more connections also left a chunk index that this download does not use.
        File(folder, "chunk_${EPISODE_ID}_3.tmp").writeBytes(video.copyOf(1024))
        val output = File(folder, "episode.mp4").apply { createNewFile() }
        val videoUrl = server.url("/episode.mp4").toString()
        val chunks = ChunkSizeCalculator.calculateChunks(video.size.toLong(), 2).mapIndexed { index, range ->
            ChunkProgress(
                index = index,
                startByte = range.startByte,
                endByte = range.endByte,
                downloadedBytes = if (index == 0) legacyChunk.length() else 0,
                status = ChunkProgress.ChunkStatus.PENDING,
                tempFileName = "chunk_${EPISODE_ID}_$index.tmp",
            )
        }
        appStore().checkpoint(
            DownloadProgress(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                totalBytes = video.size.toLong(),
                downloadedBytes = legacyChunk.length(),
                chunks = chunks,
                status = DownloadProgress.Status.PAUSED,
                entityTag = VIDEO_TAG,
                resourceUrl = videoUrl,
            ),
        ) shouldBe true

        val result = withContext(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), appStore(), maxThreadsProvider = { 2 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(folder),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
        folder.list().orEmpty().toList() shouldBe listOf(output.name)
    }

    private suspend fun CoroutineScope.interruptAfterTwoMebibytes(folder: File, output: File, videoUrl: String) {
        server.dispatcher = RangeDispatcher(video, bytesPer20Millis = 64 * 1024)
        val someBytesWritten = CountDownLatch(1)
        val download = launch(Dispatchers.IO) {
            MultiThreadDownloader(OkHttpClient(), appStore(), maxThreadsProvider = { 2 }).download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(folder),
                outputFile = documentAt(output),
                onProgress = { if (it.downloadedBytes > 2L * 1024 * 1024) someBytesWritten.countDown() },
            )
        }
        someBytesWritten.await(10, TimeUnit.SECONDS) shouldBe true
        download.cancelAndJoin()
    }

    // Each call is a new process: the store has no progress in memory.
    private fun appStore() = DownloadStateStore(mockk<Context> { every { cacheDir } returns appCache })

    // A SAF document takes writes through its stream, but scoped storage refuses new java.io.File entries in the folder.
    private fun folderWithoutFileWriteAccess(): Pair<File, File> {
        val folder = File(tempDir, "episode_tmp").apply { mkdirs() }
        val output = File(folder, "episode.mp4").apply { createNewFile() }
        folder.setWritable(false)
        lockedFolders += folder
        runCatching { File(folder, "probe").createNewFile() }.getOrDefault(false) shouldBe false
        return folder to output
    }

    private fun chunkFilesIn(directory: File): List<String> =
        directory.walkTopDown().filter { it.isFile && it.name.startsWith("chunk_") }.map { it.name }.toList()

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
            entityTag = VIDEO_TAG,
            resourceUrl = videoUrl,
        )
        val resumingStore = mockk<DownloadStateStore>(relaxed = true) {
            every { chunkDirectory } returns File(tempDir, "chunks")
            every { loadProgressIfMatching(EPISODE_ID, videoUrl) } returns savedProgress
        }
        val downloader = MultiThreadDownloader(OkHttpClient(), resumingStore, maxThreadsProvider = { 2 })

        val result = withContext(Dispatchers.IO) {
            downloader.download(
                episodeId = EPISODE_ID,
                videoUrl = videoUrl,
                headers = null,
                tmpDir = uniFileAt(storageFolder),
                outputFile = documentAt(output),
            )
        }

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().contentEquals(video) shouldBe true
    }

    private fun String.rangeStart(): Long = removePrefix("bytes=").substringBefore('-').toLong()

    private fun uniFileAt(file: File): UniFile = mockk {
        every { filePath } returns file.absolutePath
        every { listFiles() } answers {
            file.listFiles()?.map { child ->
                mockk<UniFile> {
                    every { name } returns child.name
                    every { delete() } answers { child.delete() }
                }
            }?.toTypedArray()
        }
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
                    .setHeader("ETag", VIDEO_TAG)
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
        const val VIDEO_TAG = "\"episode-v1\""

        // MP4 "ftyp" box for the signature check.
        val MP4_HEADER =
            byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    }
}
