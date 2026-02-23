package eu.kanade.tachiyomi.data.download.anime.multithread

import eu.kanade.tachiyomi.data.download.anime.resume.ChunkProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ChunkMergerTest {

    @TempDir
    lateinit var tempDir: File

    private val merger = ChunkMerger()

    @Test
    fun `mergeChunks should combine chunks in correct order`() = runTest {
        // Create test chunks
        val chunk1Data = "Hello ".toByteArray()
        val chunk2Data = "World!".toByteArray()

        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunk1Data)
        File(tempChunksDir, "chunk_1.tmp").writeBytes(chunk2Data)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = chunk1Data.size - 1L,
                    downloadedBytes = chunk1Data.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
                ChunkProgress(
                    index = 1,
                    startByte = chunk1Data.size.toLong(),
                    endByte = chunk1Data.size + chunk2Data.size - 1L,
                    downloadedBytes = chunk2Data.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_1.tmp",
                ),
            ),
            totalBytes = (chunk1Data.size + chunk2Data.size).toLong(),
        )

        val outputFile = File(tempDir, "output.mp4")

        val result = merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
        )

        result shouldBe ChunkMerger.MergeResult.Success(outputFile, (chunk1Data.size + chunk2Data.size).toLong())
        outputFile.readText() shouldBe "Hello World!"
    }

    @Test
    fun `mergeChunks should return error when chunks are incomplete`() = runTest {
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 99,
                    downloadedBytes = 50, // Incomplete
                    status = ChunkProgress.ChunkStatus.DOWNLOADING,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = 100,
        )

        val outputFile = File(tempDir, "output.mp4")

        val result = merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
        )

        result shouldBe ChunkMerger.MergeResult.Error(
            MergeError.IncompleteChunks("Chunks not complete: [0]"),
        )
    }

    @Test
    fun `mergeChunks should return error when chunk file is missing`() = runTest {
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        // Don't create the chunk file

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
            totalBytes = 100,
        )

        val outputFile = File(tempDir, "output.mp4")

        val result = merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
        )

        result shouldBe ChunkMerger.MergeResult.Error(
            MergeError.ChunkFileMissing("Chunk file not found: chunk_0.tmp"),
        )
    }

    @Test
    fun `mergeChunks should return size mismatch error when total bytes don't match`() = runTest {
        val chunkData = "Test data".toByteArray()
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunkData)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = chunkData.size - 1L,
                    downloadedBytes = chunkData.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = 9999, // Wrong expected size
        )

        val outputFile = File(tempDir, "output.mp4")

        val result = merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
        )

        result shouldBe ChunkMerger.MergeResult.Error(
            MergeError.SizeMismatch("Expected 9999 bytes, got ${chunkData.size}"),
        )
        outputFile.exists() shouldBe false // Should clean up invalid output
    }

    @Test
    fun `mergeChunks should handle single chunk`() = runTest {
        val chunkData = "Single chunk content".toByteArray()
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunkData)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = chunkData.size - 1L,
                    downloadedBytes = chunkData.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = chunkData.size.toLong(),
        )

        val outputFile = File(tempDir, "output.mp4")

        val result = merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
        )

        result shouldBe ChunkMerger.MergeResult.Success(outputFile, chunkData.size.toLong())
        outputFile.readText() shouldBe "Single chunk content"
    }

    @Test
    fun `mergeChunks should report progress during merge`() = runTest {
        val chunkData = "Progress test data".toByteArray()
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunkData)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = chunkData.size - 1L,
                    downloadedBytes = chunkData.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = chunkData.size.toLong(),
        )

        val outputFile = File(tempDir, "output.mp4")
        val progressUpdates = mutableListOf<Long>()

        merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
            onProgress = { bytes -> progressUpdates.add(bytes) },
        )

        progressUpdates.sum() shouldBe chunkData.size.toLong()
    }

    @Test
    fun `verifyChunks should return true when all chunks are valid`() {
        val chunkData = "Test data".toByteArray()
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunkData)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = chunkData.size - 1L,
                    downloadedBytes = chunkData.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = chunkData.size.toLong(),
        )

        merger.verifyChunks(progress, tempChunksDir) shouldBe true
    }

    @Test
    fun `verifyChunks should return false when chunk is incomplete`() {
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 99,
                    downloadedBytes = 50,
                    status = ChunkProgress.ChunkStatus.DOWNLOADING, // Not completed
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = 100,
        )

        merger.verifyChunks(progress, tempChunksDir) shouldBe false
    }

    @Test
    fun `verifyChunks should return false when chunk file is missing`() {
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 99,
                    downloadedBytes = 100,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp", // File doesn't exist
                ),
            ),
            totalBytes = 100,
        )

        merger.verifyChunks(progress, tempChunksDir) shouldBe false
    }

    @Test
    fun `verifyChunks should handle open-ended range`() {
        val chunkData = "Open ended content".toByteArray()
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunkData)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = -1, // Open-ended range
                    downloadedBytes = chunkData.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
            ),
            totalBytes = -1, // Unknown total
        )

        // For open-ended ranges, isComplete returns false (can't verify without knowing total size)
        // So verifyChunks should return false
        merger.verifyChunks(progress, tempChunksDir) shouldBe false
    }

    @Test
    fun `calculateTotalChunkSize should return correct size`() {
        val chunk1Data = "Chunk1".toByteArray()
        val chunk2Data = "Chunk2Data".toByteArray()
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes(chunk1Data)
        File(tempChunksDir, "chunk_1.tmp").writeBytes(chunk2Data)

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = chunk1Data.size - 1L,
                    downloadedBytes = chunk1Data.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
                ChunkProgress(
                    index = 1,
                    startByte = chunk1Data.size.toLong(),
                    endByte = chunk1Data.size + chunk2Data.size - 1L,
                    downloadedBytes = chunk2Data.size.toLong(),
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_1.tmp",
                ),
            ),
            totalBytes = (chunk1Data.size + chunk2Data.size).toLong(),
        )

        merger.calculateTotalChunkSize(progress, tempChunksDir) shouldBe (chunk1Data.size + chunk2Data.size).toLong()
    }

    @Test
    fun `calculateTotalChunkSize should return -1 when chunk is missing`() {
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 99,
                    downloadedBytes = 100,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp", // Missing
                ),
            ),
            totalBytes = 100,
        )

        merger.calculateTotalChunkSize(progress, tempChunksDir) shouldBe -1
    }

    @Test
    fun `mergeChunks should return error for non-sequential chunk indices`() = runTest {
        val tempChunksDir = File(tempDir, "chunks").apply { mkdirs() }
        File(tempChunksDir, "chunk_0.tmp").writeBytes("data".toByteArray())
        File(tempChunksDir, "chunk_2.tmp").writeBytes("more".toByteArray())

        val progress = createTestProgress(
            chunks = listOf(
                ChunkProgress(
                    index = 0,
                    startByte = 0,
                    endByte = 3,
                    downloadedBytes = 4,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_0.tmp",
                ),
                ChunkProgress(
                    index = 2, // Gap - missing index 1
                    startByte = 4,
                    endByte = 7,
                    downloadedBytes = 4,
                    status = ChunkProgress.ChunkStatus.COMPLETED,
                    tempFileName = "chunk_2.tmp",
                ),
            ),
            totalBytes = 8,
        )

        val outputFile = File(tempDir, "output.mp4")

        val result = merger.mergeChunks(
            progress = progress,
            tempDir = tempChunksDir,
            outputFile = outputFile,
        )

        result shouldBe ChunkMerger.MergeResult.Error(
            MergeError.IncompleteChunks(
                "Non-sequential chunks. Expected: [0, 1], Got: [0, 2]",
            ),
        )
    }

    private fun createTestProgress(
        chunks: List<ChunkProgress>,
        totalBytes: Long,
    ): DownloadProgress {
        return DownloadProgress(
            episodeId = 1L,
            videoUrl = "http://example.com/video.mp4",
            totalBytes = totalBytes,
            downloadedBytes = chunks.sumOf { it.downloadedBytes },
            chunks = chunks,
            status = DownloadProgress.Status.IN_PROGRESS,
        )
    }
}
