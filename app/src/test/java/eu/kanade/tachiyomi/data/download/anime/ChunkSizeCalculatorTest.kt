package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.data.download.anime.resume.ChunkSizeCalculator
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ChunkSizeCalculatorTest {

    @Test
    fun `calculateChunks returns single chunk for small files`() {
        val totalSize = 4L * 1024 * 1024 // 4 MB
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, 4)

        chunks shouldHaveSize 1
        chunks[0].startByte shouldBe 0
        chunks[0].endByte shouldBe totalSize - 1
    }

    @ParameterizedTest
    @CsvSource(
        "104857600, 4, 4", // 100 MB with 4 threads = 4 chunks
        "104857600, 2, 2", // 100 MB with 2 threads = 2 chunks
        "52428800, 4, 2", // 50 MB with 4 threads = 2 chunks (50MB / 4 threads = 12.5MB < 25MB min)
        "209715200, 4, 4", // 200 MB with 4 threads = 4 chunks
        "419430400, 4, 4", // 400 MB with 4 threads = 4 chunks
    )
    fun `calculateChunks returns correct number of chunks`(totalSize: Long, threadCount: Int, expectedChunks: Int) {
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, threadCount)

        chunks shouldHaveSize expectedChunks
    }

    @Test
    fun `calculateChunks splits ranges correctly`() {
        val totalSize = 100L * 1024 * 1024 // 100 MB
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, 4)

        chunks shouldHaveSize 4

        // First chunk should start at 0
        chunks[0].startByte shouldBe 0

        // Last chunk should end at totalSize - 1
        chunks[3].endByte shouldBe totalSize - 1

        // Chunks should be contiguous (no gaps, no overlaps)
        for (i in 0 until chunks.size - 1) {
            chunks[i].endByte + 1 shouldBe chunks[i + 1].startByte
        }
    }

    @Test
    fun `calculateChunks respects minimum chunk size`() {
        val totalSize = 20L * 1024 * 1024 // 20 MB (less than 25MB min chunk size)
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, 4)

        // Should reduce to 1 chunk since 20MB/4 = 5MB < 25MB min
        chunks shouldHaveSize 1
    }

    @Test
    fun `calculateChunks respects maximum chunk count`() {
        val totalSize = 500L * 1024 * 1024 // 500 MB
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, 8) // Request 8 threads

        // Should be capped at 4 chunks (MAX_CHUNKS)
        chunks shouldHaveSize 4
    }

    @Test
    fun `calculateChunks handles exact divisions`() {
        val totalSize = 100L * 1024 * 1024 // 100 MB
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, 4)

        // Each chunk should be exactly 25 MB
        chunks shouldHaveSize 4
        for (chunk in chunks) {
            val chunkSize = chunk.endByte - chunk.startByte + 1
            chunkSize shouldBe 25L * 1024 * 1024
        }
    }

    @Test
    fun `calculateChunks handles uneven divisions`() {
        val totalSize = 100L * 1024 * 1024 + 1024 // 100 MB + 1 KB
        val chunks = ChunkSizeCalculator.calculateChunks(totalSize, 4)

        chunks shouldHaveSize 4

        // Last chunk should get the extra bytes
        val lastChunk = chunks.last()
        val lastChunkSize = lastChunk.endByte - lastChunk.startByte + 1
        lastChunkSize shouldBe (25L * 1024 * 1024 + 1024)
    }

    @Test
    fun `calculateChunks clamps thread count`() {
        val totalSize = 200L * 1024 * 1024 // 200 MB

        // Test with thread count < 1
        val chunksZero = ChunkSizeCalculator.calculateChunks(totalSize, 0)
        chunksZero shouldHaveSize 1

        // Test with thread count > 4
        val chunksMany = ChunkSizeCalculator.calculateChunks(totalSize, 10)
        chunksMany shouldHaveSize 4
    }

    @Test
    fun `calculateChunks returns empty list for zero size`() {
        val chunks = ChunkSizeCalculator.calculateChunks(0, 4)

        chunks shouldHaveSize 0
    }

    @Test
    fun `calculateChunks returns single chunk for negative size`() {
        val chunks = ChunkSizeCalculator.calculateChunks(-1, 4)

        chunks shouldHaveSize 0
    }
}
