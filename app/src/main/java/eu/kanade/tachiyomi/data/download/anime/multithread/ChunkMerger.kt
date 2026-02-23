package eu.kanade.tachiyomi.data.download.anime.multithread

import eu.kanade.tachiyomi.data.download.anime.resume.ChunkProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Merges downloaded chunks into a single output file.
 *
 * This class handles:
 * - Sequential merging of chunks in correct order
 * - Progress reporting during merge
 * - Validation of merged file size
 * - Cleanup of temporary chunk files
 * - Handling merge failures (disk full, etc.)
 */
class ChunkMerger {

    /**
     * Result of a merge operation.
     */
    sealed class MergeResult {
        /**
         * Merge completed successfully.
         */
        data class Success(
            val outputFile: File,
            val totalBytes: Long,
        ) : MergeResult()

        /**
         * Merge failed with an error.
         */
        data class Error(
            val error: MergeError,
        ) : MergeResult()

        /**
         * Merge was cancelled.
         */
        data object Cancelled : MergeResult()
    }

    /**
     * Merges chunks into a single output file.
     *
     * @param progress The download progress containing chunk information
     * @param tempDir The directory containing chunk files
     * @param outputFile The final output file path
     * @param onProgress Callback for merge progress (bytes written)
     * @return [MergeResult] indicating success or failure
     */
    suspend fun mergeChunks(
        progress: DownloadProgress,
        tempDir: File,
        outputFile: File,
        onProgress: (Long) -> Unit = {},
    ): MergeResult {
        return withContext(Dispatchers.IO) {
            // Check for cancellation
            if (!coroutineContext.isActive) {
                return@withContext MergeResult.Cancelled
            }

            try {
                // Create output directory if needed
                outputFile.parentFile?.mkdirs()

                // Delete existing output file if present
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // Sort chunks by index to ensure correct order
                val sortedChunks = progress.chunks.sortedBy { it.index }

                // Verify chunk indices are sequential (no gaps)
                val expectedIndices = (0 until sortedChunks.size).toList()
                val actualIndices = sortedChunks.map { it.index }
                if (actualIndices != expectedIndices) {
                    return@withContext MergeResult.Error(
                        MergeError.IncompleteChunks(
                            "Non-sequential chunks. Expected: $expectedIndices, Got: $actualIndices",
                        ),
                    )
                }

                // Verify all chunks are complete
                val incompleteChunks = sortedChunks.filter { !it.isComplete }
                if (incompleteChunks.isNotEmpty()) {
                    return@withContext MergeResult.Error(
                        MergeError.IncompleteChunks(
                            "Chunks not complete: ${incompleteChunks.map { it.index }}",
                        ),
                    )
                }

                // Perform the merge
                var totalBytesWritten = 0L

                outputFile.outputStream().sink().buffer().use { outputSink ->
                    for (chunk in sortedChunks) {
                        // Check for cancellation
                        if (!coroutineContext.isActive) {
                            return@withContext MergeResult.Cancelled
                        }

                        val chunkFile = File(tempDir, chunk.tempFileName)

                        if (!chunkFile.exists()) {
                            return@withContext MergeResult.Error(
                                MergeError.ChunkFileMissing(
                                    "Chunk file not found: ${chunk.tempFileName}",
                                ),
                            )
                        }

                        // Copy chunk to output
                        chunkFile.inputStream().source().buffer().use { inputSource ->
                            var bytesRead: Long
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                            while (inputSource.read(buffer).also { bytesRead = it.toLong() } != -1) {
                                outputSink.write(buffer, 0, bytesRead.toInt())
                                totalBytesWritten += bytesRead
                                onProgress(bytesRead)
                            }
                        }

                        outputSink.flush()
                    }
                }

                // Validate merged file size
                val expectedSize = progress.totalBytes
                if (expectedSize > 0 && outputFile.length() != expectedSize) {
                    logcat(LogPriority.ERROR) {
                        "Merged file size mismatch: expected $expectedSize, got ${outputFile.length()}"
                    }

                    // Clean up invalid output
                    outputFile.delete()

                    return@withContext MergeResult.Error(
                        MergeError.SizeMismatch(
                            "Expected $expectedSize bytes, got ${outputFile.length()}",
                        ),
                    )
                }

                // Clean up temp files on success
                cleanupTempFiles(sortedChunks, tempDir)

                MergeResult.Success(outputFile, totalBytesWritten)
            } catch (e: IOException) {
                when {
                    e.message?.contains("ENOSPC") == true ||
                        e.message?.contains("No space left") == true -> {
                        MergeResult.Error(MergeError.DiskFull())
                    }
                    else -> MergeResult.Error(MergeError.IOError(e))
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Unexpected error during chunk merge: ${e.message}" }
                MergeResult.Error(MergeError.IOError(e))
            }
        }
    }

    /**
     * Verifies that all chunks exist and are valid without merging.
     *
     * @param progress The download progress
     * @param tempDir The directory containing chunk files
     * @return true if all chunks are ready for merging
     */
    fun verifyChunks(
        progress: DownloadProgress,
        tempDir: File,
    ): Boolean {
        val sortedChunks = progress.chunks.sortedBy { it.index }

        for (chunk in sortedChunks) {
            if (!chunk.isComplete) {
                logcat(LogPriority.WARN) { "Chunk ${chunk.index} is not complete" }
                return false
            }

            val chunkFile = File(tempDir, chunk.tempFileName)
            if (!chunkFile.exists()) {
                logcat(LogPriority.WARN) { "Chunk file missing: ${chunk.tempFileName}" }
                return false
            }

            // Verify chunk file size
            val expectedSize = chunk.endByte - chunk.startByte + 1
            if (chunkFile.length() != expectedSize) {
                logcat(LogPriority.WARN) {
                    "Chunk ${chunk.index} size mismatch: expected $expectedSize, got ${chunkFile.length()}"
                }
                return false
            }
        }

        return true
    }

    /**
     * Calculates the total size of all chunk files.
     *
     * @param progress The download progress
     * @param tempDir The directory containing chunk files
     * @return Total size in bytes, or -1 if any chunk is missing
     */
    fun calculateTotalChunkSize(
        progress: DownloadProgress,
        tempDir: File,
    ): Long {
        var totalSize = 0L

        for (chunk in progress.chunks) {
            val chunkFile = File(tempDir, chunk.tempFileName)
            if (!chunkFile.exists()) {
                return -1
            }
            totalSize += chunkFile.length()
        }

        return totalSize
    }

    /**
     * Cleans up temporary chunk files.
     */
    private fun cleanupTempFiles(chunks: List<ChunkProgress>, tempDir: File) {
        for (chunk in chunks) {
            try {
                val chunkFile = File(tempDir, chunk.tempFileName)
                if (chunkFile.exists()) {
                    chunkFile.delete()
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN) { "Failed to delete chunk file: ${chunk.tempFileName}: ${e.message}" }
            }
        }
    }

    companion object {
        /**
         * Minimum free space required for merge operation (50 MB).
         */
        const val MIN_FREE_SPACE_BYTES = 50L * 1024 * 1024
    }
}
