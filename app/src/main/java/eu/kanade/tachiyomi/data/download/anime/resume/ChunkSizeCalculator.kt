package eu.kanade.tachiyomi.data.download.anime.resume

import kotlin.math.min

/**
 * Calculates optimal chunk sizes for multi-threaded downloads.
 *
 * The calculator ensures:
 * - Minimum chunk size to avoid excessive overhead (25 MB)
 * - Maximum chunk size to allow reasonable progress granularity (100 MB)
 * - Maximum number of chunks to limit resource usage (4)
 */
object ChunkSizeCalculator {

    /**
     * Minimum chunk size: 25 MB
     * Chunks smaller than this cause excessive overhead from HTTP request/response.
     */
    private const val MIN_CHUNK_SIZE = 25L * 1024 * 1024

    /**
     * Maximum chunk size: 100 MB
     * Chunks larger than this reduce the effectiveness of parallel downloading.
     */
    private const val MAX_CHUNK_SIZE = 100L * 1024 * 1024

    /**
     * Maximum number of chunks per download.
     * This limits concurrent connections and resource usage.
     */
    const val MAX_CHUNKS = 4

    /**
     * Minimum number of bytes required to use multi-threaded downloading.
     * Files smaller than this use single-threaded download.
     */
    private const val MIN_SIZE_FOR_MULTI_THREAD = MIN_CHUNK_SIZE

    /**
     * Calculates chunk ranges for a multi-threaded download.
     *
     * The algorithm:
     * 1. Calculate max chunks based on MIN_CHUNK_SIZE (each chunk must be at least MIN_CHUNK_SIZE)
     * 2. Limit chunks to requested thread count and MAX_CHUNKS
     * 3. Base chunk size is MIN_CHUNK_SIZE (not total/count)
     * 4. Last chunk gets any remaining bytes
     *
     * @param totalSize The total size of the file in bytes
     * @param threadCount The desired number of threads (1-4, will be clamped)
     * @return A list of [ChunkRange] objects representing byte ranges for each chunk
     */
    fun calculateChunks(totalSize: Long, threadCount: Int): List<ChunkRange> {
        // Validate input
        if (totalSize <= 0) {
            return emptyList()
        }

        // If threadCount is < 1, return single chunk
        if (threadCount < 1) {
            return listOf(ChunkRange(0, totalSize - 1))
        }

        // Clamp thread count to valid range
        val clampedThreadCount = threadCount.coerceIn(1, MAX_CHUNKS)

        // For small files, use single chunk
        if (totalSize < MIN_SIZE_FOR_MULTI_THREAD) {
            return listOf(ChunkRange(0, totalSize - 1))
        }

        // Calculate how many chunks we can have based on minimum chunk size
        val maxChunksBySize = (totalSize / MIN_CHUNK_SIZE).toInt()

        // The actual chunk count is the minimum of:
        // - requested thread count
        // - max chunks by size (ensures each chunk meets minimum size)
        val actualChunkCount = min(clampedThreadCount, maxChunksBySize).coerceAtLeast(1)

        // If we end up with 1 chunk, return simple range
        if (actualChunkCount == 1) {
            return listOf(ChunkRange(0, totalSize - 1))
        }

        // Calculate chunk boundaries
        // Base chunk size is MIN_CHUNK_SIZE, last chunk gets remainder
        val chunks = mutableListOf<ChunkRange>()
        var currentByte = 0L

        for (i in 0 until actualChunkCount) {
            val chunkSize = when {
                // First n-1 chunks get MIN_CHUNK_SIZE
                i < actualChunkCount - 1 -> MIN_CHUNK_SIZE
                // Last chunk gets remaining bytes
                else -> totalSize - currentByte
            }
            val endByte = currentByte + chunkSize - 1

            chunks.add(ChunkRange(currentByte, endByte))
            currentByte = endByte + 1
        }

        return chunks
    }

    /**
     * Determines if multi-threaded downloading should be used for a file of given size.
     *
     * @param totalSize The total size of the file in bytes
     * @return true if multi-threaded download is recommended
     */
    fun shouldUseMultiThread(totalSize: Long): Boolean {
        return totalSize >= MIN_SIZE_FOR_MULTI_THREAD
    }

    /**
     * Gets the recommended thread count for a file of given size.
     *
     * @param totalSize The total size of the file in bytes
     * @param maxThreads The maximum threads the user allows
     * @return The recommended number of threads (1-4)
     */
    fun getRecommendedThreadCount(totalSize: Long, maxThreads: Int): Int {
        if (totalSize < MIN_SIZE_FOR_MULTI_THREAD) {
            return 1
        }

        val clampedMax = maxThreads.coerceIn(1, MAX_CHUNKS)
        val maxChunksBySize = (totalSize / MIN_CHUNK_SIZE).toInt()

        return min(clampedMax, maxChunksBySize).coerceAtLeast(1)
    }
}
