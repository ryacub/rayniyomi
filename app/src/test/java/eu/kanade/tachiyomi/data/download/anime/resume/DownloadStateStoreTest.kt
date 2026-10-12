package eu.kanade.tachiyomi.data.download.anime.resume

import android.content.Context
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DownloadStateStoreTest {

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class TimestampedProgress(
        @ProtoNumber(1) val episodeId: Long,
        @ProtoNumber(2) val videoUrl: String,
        @ProtoNumber(3) val totalBytes: Long,
        @ProtoNumber(4) val downloadedBytes: Long,
        @ProtoNumber(5) val chunks: List<ChunkProgress>,
        @ProtoNumber(6) val status: DownloadProgress.Status,
        @ProtoNumber(7) val createdAt: Long,
        @ProtoNumber(8) val updatedAt: Long,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `loadProgress reads a file saved with creation and update timestamps`(@TempDir cacheDir: File) {
        val chunk = ChunkProgress(
            index = 0,
            startByte = 0,
            endByte = 99,
            downloadedBytes = 40,
            status = ChunkProgress.ChunkStatus.DOWNLOADING,
            tempFileName = "chunk_0.tmp",
        )
        val saved = TimestampedProgress(
            episodeId = 7L,
            videoUrl = "http://example.com/video.mp4",
            totalBytes = 100L,
            downloadedBytes = 40L,
            chunks = listOf(chunk),
            status = DownloadProgress.Status.PAUSED,
            createdAt = 1_000L,
            updatedAt = 2_000L,
        )
        val context = mockk<Context> { every { this@mockk.cacheDir } returns cacheDir }
        val store = DownloadStateStore(context)
        File(cacheDir, "download_progress/7.dp").writeBytes(ProtoBuf.encodeToByteArray(saved))

        val loaded = store.loadProgress(7L)

        loaded?.episodeId shouldBe 7L
        loaded?.videoUrl shouldBe "http://example.com/video.mp4"
        loaded?.totalBytes shouldBe 100L
        loaded?.downloadedBytes shouldBe 40L
        loaded?.chunks shouldBe listOf(chunk)
        loaded?.status shouldBe DownloadProgress.Status.PAUSED
    }

    @Test
    fun `progressPercent should calculate correctly`() {
        val progress = createTestProgress(
            totalBytes = 1000L,
            downloadedBytes = 250L,
        )

        progress.progressPercent shouldBe 25
    }

    @Test
    fun `progressPercent should return -1 when totalBytes unknown`() {
        val progress = createTestProgress(
            totalBytes = -1L,
            downloadedBytes = 500L,
        )

        progress.progressPercent shouldBe -1
    }

    @Test
    fun `isComplete should return true when all chunks complete`() {
        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 99,
                    downloadedBytes = 100,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
        )

        progress.isComplete shouldBe true
    }

    @Test
    fun `isComplete should return false when chunks incomplete`() {
        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 99,
                    downloadedBytes = 50,
                    status = ChunkProgress.ChunkStatus.DOWNLOADING,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
        )

        progress.isComplete shouldBe false
    }

    @Test
    fun `should handle chunks with progress`() {
        val progress = createTestProgress(
            totalBytes = 1000L,
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 499,
                    downloadedBytes = 250L,
                    status = ChunkProgress.ChunkStatus.DOWNLOADING,
                    tempFileName = "chunk_0.tmp",
                ),
                ChunkProgress(
                    index = 1,
                    startByte = 500,
                    endByte = 999,
                    downloadedBytes = 500L,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_1.tmp",
                ),
            ),
        )

        progress.chunks[0].progressPercent shouldBe 50 // 250/500
        progress.chunks[1].progressPercent shouldBe 100 // 500/500
        progress.isComplete shouldBe false
    }

    @Test
    fun `should handle empty chunks list`() {
        val progress = createTestProgress(
            chunks = emptyList(),
            totalBytes = 1000L,
        )

        progress.isComplete shouldBe true // vacuously true
        progress.progressPercent shouldBe 0 // no downloaded bytes
    }

    @Test
    fun `withUpdatedTimestamp should update timestamp`() {
        val oldTime = System.currentTimeMillis() - 10000
        val progress = createTestProgress(
            episodeId = 1L,
            updatedAt = oldTime,
        )

        val updated = progress.withUpdatedTimestamp()

        updated.updatedAt shouldNotBe oldTime
    }

    @Test
    fun `ChunkRange should calculate size correctly`() {
        val range = ChunkRange(startByte = 0, endByte = 99)
        range.size shouldBe 100
    }

    @Test
    fun `ChunkRange should handle open-ended range`() {
        val range = ChunkRange(startByte = 0, endByte = -1)
        range.size shouldBe -1
    }

    @Test
    fun `ChunkRange should generate correct Range header`() {
        val range = ChunkRange(startByte = 0, endByte = 99)
        range.toRangeHeader() shouldBe "bytes=0-99"
    }

    @Test
    fun `ChunkRange should generate open-ended Range header`() {
        val range = ChunkRange(startByte = 100, endByte = -1)
        range.toRangeHeader() shouldBe "bytes=100-"
    }

    @Test
    fun `ChunkProgress should detect completion correctly`() {
        val completeChunk = ChunkProgress(
            index = 0,
            startByte = 0,
            endByte = 99,
            downloadedBytes = 100,
            status = ChunkProgress.ChunkStatus.COMPLETED,
            tempFileName = "chunk_0.tmp",
        )

        val incompleteChunk = ChunkProgress(
            index = 0,
            startByte = 0,
            endByte = 99,
            downloadedBytes = 50,
            status = ChunkProgress.ChunkStatus.DOWNLOADING,
            tempFileName = "chunk_0.tmp",
        )

        completeChunk.isComplete shouldBe true
        incompleteChunk.isComplete shouldBe false
    }

    @Test
    fun `ChunkProgress should handle open-ended range`() {
        val chunk = ChunkProgress(
            index = 0,
            startByte = 0,
            endByte = -1, // Open-ended
            downloadedBytes = 1000,
            status = ChunkProgress.ChunkStatus.COMPLETED,
            tempFileName = "chunk_0.tmp",
        )

        chunk.isComplete shouldBe false // Can't determine completion for open-ended
        chunk.totalBytes shouldBe -1
    }

    private fun createTestProgress(
        episodeId: Long = 1L,
        videoUrl: String = "http://example.com/video.mp4",
        totalBytes: Long = 1000L,
        downloadedBytes: Long = 0L,
        status: DownloadProgress.Status = DownloadProgress.Status.IN_PROGRESS,
        updatedAt: Long = System.currentTimeMillis(),
        chunks: List<ChunkProgress> = emptyList(),
    ): DownloadProgress {
        return DownloadProgress(
            episodeId = episodeId,
            videoUrl = videoUrl,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            status = status,
            updatedAt = updatedAt,
            chunks = chunks,
        )
    }
}
