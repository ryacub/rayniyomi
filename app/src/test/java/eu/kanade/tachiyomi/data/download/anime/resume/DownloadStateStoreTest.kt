package eu.kanade.tachiyomi.data.download.anime.resume

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class DownloadStateStoreTest {

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
