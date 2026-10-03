package eu.kanade.tachiyomi.data.download.anime.multithread

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkProgress
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkRange
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadStateStore
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MultiThreadDownloaderFailureTest {

    @TempDir
    lateinit var tempDir: File

    private val stateStore = mockk<DownloadStateStore>(relaxed = true)
    private val chunkDownloader = mockk<ChunkDownloader>()

    private val progress = DownloadProgress(
        episodeId = EPISODE_ID,
        videoUrl = "https://example.test/episode.mp4",
        totalBytes = 20,
        chunks = listOf(chunkAt(0, 0, 9), chunkAt(1, 10, 19)),
    )

    @Test
    fun `an invalid range in one chunk reaches the caller as InvalidRange`() = runTest {
        stubChunks(
            0 to ChunkDownloader.ChunkDownloadResult.Error(DownloadError.InvalidRange("Range is not satisfiable")),
            1 to ChunkDownloader.ChunkDownloadResult.Success(CHUNK_SIZE),
        )

        val result = resumeDownload()

        result.shouldBeInstanceOf<DownloadResult.Error>()
        result.error shouldBe DownloadError.InvalidRange("Range is not satisfiable")
    }

    @Test
    fun `a full disk in one chunk reaches the caller as DiskFull`() = runTest {
        stubChunks(
            0 to ChunkDownloader.ChunkDownloadResult.Success(CHUNK_SIZE),
            1 to ChunkDownloader.ChunkDownloadResult.Error(DownloadError.DiskFull()),
        )

        val result = resumeDownload()

        result.shouldBeInstanceOf<DownloadResult.Error>()
        result.error.shouldBeInstanceOf<DownloadError.DiskFull>()
    }

    @Test
    fun `DiskFull wins when another chunk fails with a different error`() = runTest {
        stubChunks(
            0 to ChunkDownloader.ChunkDownloadResult.Error(DownloadError.InvalidRange("Range is not satisfiable")),
            1 to ChunkDownloader.ChunkDownloadResult.Error(DownloadError.DiskFull()),
        )

        val result = resumeDownload()

        result.shouldBeInstanceOf<DownloadResult.Error>()
        result.error.shouldBeInstanceOf<DownloadError.DiskFull>()
    }

    @Test
    fun `any other chunk error keeps its type`() = runTest {
        stubChunks(
            0 to ChunkDownloader.ChunkDownloadResult.Error(DownloadError.ClientError("Client error: 403")),
            1 to ChunkDownloader.ChunkDownloadResult.Success(CHUNK_SIZE),
        )

        val result = resumeDownload()

        result.shouldBeInstanceOf<DownloadResult.Error>()
        result.error shouldBe DownloadError.ClientError("Client error: 403")
    }

    @Test
    fun `a cancelled chunk cancels the operation`() = runTest {
        stubChunks(
            0 to ChunkDownloader.ChunkDownloadResult.Cancelled,
            1 to ChunkDownloader.ChunkDownloadResult.Success(CHUNK_SIZE),
        )

        resumeDownload() shouldBe DownloadResult.Cancelled
    }

    @Test
    fun `completed chunks merge into the output file`() = runTest {
        stubChunks(
            0 to ChunkDownloader.ChunkDownloadResult.Success(CHUNK_SIZE),
            1 to ChunkDownloader.ChunkDownloadResult.Success(CHUNK_SIZE),
        )
        val output = File(tempDir, "episode.mp4")

        val result = resumeDownload(output)

        result.shouldBeInstanceOf<DownloadResult.Success>()
        output.readBytes().toList() shouldBe (ByteArray(10) { 0 } + ByteArray(10) { 1 }).toList()
    }

    private suspend fun resumeDownload(output: File = File(tempDir, "unused.mp4")): DownloadResult {
        val downloader = MultiThreadDownloader(OkHttpClient(), stateStore, chunkDownloader = chunkDownloader)
        return downloader.resume(progress, null, uniFileAt(chunkDir), documentAt(output))
    }

    // A chunk that reports Success also leaves its full temp file behind, as the real downloader does.
    private fun stubChunks(vararg results: Pair<Int, ChunkDownloader.ChunkDownloadResult>) {
        val resultByStart = results.associate { (index, result) -> progress.chunks[index].startByte to result }
        coEvery {
            chunkDownloader.downloadChunk(any(), any(), any(), any(), any(), any())
        } answers {
            val range = arg<ChunkRange>(1)
            val tempFile = arg<File>(3)
            val result = resultByStart.getValue(range.startByte)
            if (result is ChunkDownloader.ChunkDownloadResult.Success) {
                tempFile.writeBytes(ByteArray(CHUNK_SIZE.toInt()) { if (range.startByte == 0L) 0 else 1 })
                arg<(Long) -> Unit>(5)(result.bytesDownloaded)
            }
            result
        }
    }

    private val chunkDir: File
        get() = File(tempDir, "chunks").apply { mkdirs() }

    private fun chunkAt(index: Int, start: Long, end: Long) = ChunkProgress(
        index = index,
        startByte = start,
        endByte = end,
        tempFileName = "chunk_${EPISODE_ID}_$index.tmp",
    )

    private fun uniFileAt(file: File): UniFile = mockk {
        every { filePath } returns file.absolutePath
    }

    private fun documentAt(file: File): UniFile = mockk {
        every { filePath } returns null
        every { openOutputStream() } answers { file.outputStream() }
        every { delete() } answers { file.delete() }
    }

    private companion object {
        const val EPISODE_ID = 7L
        const val CHUNK_SIZE = 10L
    }
}
