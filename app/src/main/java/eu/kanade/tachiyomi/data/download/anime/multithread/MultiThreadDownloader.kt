package eu.kanade.tachiyomi.data.download.anime.multithread

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkProgress
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkRange
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkSizeCalculator
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadStateStore
import eu.kanade.tachiyomi.data.download.anime.resume.RangeRequestHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Downloads video files using multiple concurrent connections.
 *
 * This downloader provides:
 * - Multi-threaded downloading with configurable thread count (1-4)
 * - Resume capability for interrupted downloads
 * - Progress persistence for recovery
 * - Automatic chunk management
 * - Integration with existing download infrastructure
 */
class MultiThreadDownloader(
    private val client: OkHttpClient,
    private val stateStore: DownloadStateStore,
    private val maxThreadsProvider: () -> Int = { DEFAULT_THREAD_COUNT },
) {
    private val rangeRequestHandler = RangeRequestHandler(client)
    private val chunkDownloader = ChunkDownloader(client, rangeRequestHandler)
    private val chunkMerger = ChunkMerger()

    /**
     * Scope for this download operation. Cancelled when download completes or fails.
     */
    private var downloadScope: CoroutineScope? = null

    /**
     * Active download jobs for cancellation.
     * Using CopyOnWriteArrayList for thread-safe access from multiple coroutines.
     */
    private val activeJobs = CopyOnWriteArrayList<Job>()

    /**
     * Downloads a video file using multiple threads.
     *
     * @param episodeId The episode ID for progress tracking
     * @param videoUrl The URL to download from
     * @param headers Optional headers to include in requests
     * @param tmpDir The temporary directory for chunk files
     * @param outputFile The final output file
     * @param onProgress Callback for download progress updates
     * @return [DownloadResult] indicating success or failure
     */
    suspend fun download(
        episodeId: Long,
        videoUrl: String,
        headers: Headers?,
        tmpDir: UniFile,
        outputFile: UniFile,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        // Check for existing progress
        val existingProgress = stateStore.loadProgressIfMatching(episodeId, videoUrl)

        return if (existingProgress != null && existingProgress.status != DownloadProgress.Status.COMPLETED) {
            logcat(LogPriority.INFO) { "Resuming download for episode $episodeId" }
            resume(existingProgress, headers, tmpDir, outputFile, onProgress)
        } else {
            startNewDownload(episodeId, videoUrl, headers, tmpDir, outputFile, onProgress)
        }
    }

    /**
     * Resumes a partially completed download.
     *
     * @param progress The saved progress state
     * @param headers Optional headers to include
     * @param tmpDir The temporary directory
     * @param outputFile The final output file
     * @param onProgress Callback for progress updates
     * @return [DownloadResult] indicating success or failure
     */
    suspend fun resume(
        progress: DownloadProgress,
        headers: Headers?,
        tmpDir: UniFile,
        outputFile: UniFile,
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        return performDownload(progress, headers, tmpDir, outputFile, onProgress, resume = true)
    }

    /**
     * Starts a new download.
     */
    private suspend fun startNewDownload(
        episodeId: Long,
        videoUrl: String,
        headers: Headers?,
        tmpDir: UniFile,
        outputFile: UniFile,
        onProgress: (DownloadProgress) -> Unit,
    ): DownloadResult {
        // Check range support
        val rangeSupport = rangeRequestHandler.checkRangeSupport(videoUrl, headers)

        val totalBytes = when (rangeSupport) {
            is RangeRequestHandler.RangeSupportResult.Supported -> rangeSupport.totalSize
            else -> -1L
        }

        // Get current thread count from provider (respects runtime preference changes)
        val maxThreads = maxThreadsProvider()

        // Calculate chunks
        val chunks = if (totalBytes > 0) {
            ChunkSizeCalculator.calculateChunks(totalBytes, maxThreads)
        } else {
            // Unknown size, use single chunk with open-ended range
            // This tells the server to send from byte 0 to the end
            listOf(ChunkRange(0, -1))
        }

        // Create progress
        val progress = DownloadProgress(
            episodeId = episodeId,
            videoUrl = videoUrl,
            totalBytes = totalBytes,
            downloadedBytes = 0,
            chunks = chunks.mapIndexed { index, range ->
                ChunkProgress(
                    index = index,
                    startByte = range.startByte,
                    endByte = range.endByte,
                    downloadedBytes = 0,
                    status = ChunkProgress.ChunkStatus.PENDING,
                    tempFileName = "chunk_${episodeId}_$index.tmp",
                )
            },
            status = DownloadProgress.Status.IN_PROGRESS,
        )

        return performDownload(progress, headers, tmpDir, outputFile, onProgress, resume = false)
    }

    /**
     * Performs the actual download operation.
     */
    private suspend fun performDownload(
        progress: DownloadProgress,
        headers: Headers?,
        tmpDir: UniFile,
        outputFile: UniFile,
        onProgress: (DownloadProgress) -> Unit,
        resume: Boolean,
    ): DownloadResult {
        // Create supervisor scope that is a child of the caller's scope
        // This ensures proper cancellation propagation while allowing independent job management
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        downloadScope = scope

        // Get temp directory as File
        val tempDirFile = File(
            tmpDir.filePath ?: return DownloadResult.Error(
                DownloadError.InvalidTempDirectory("Cannot access temp directory"),
            ),
        )

        // Get output file as File
        val outputFilePath = outputFile.filePath ?: return DownloadResult.Error(
            DownloadError.InvalidOutputFile("Cannot access output file"),
        )
        val outputFileObj = File(outputFilePath)

        // Track total downloaded bytes
        val totalDownloaded = AtomicLong(progress.downloadedBytes)

        return try {
            // Create jobs for each incomplete chunk
            val chunkJobs = progress.chunks
                .filter { !it.isComplete }
                .map { chunk ->
                    scope.launch {
                        downloadChunkWithRetry(
                            chunk = chunk,
                            videoUrl = progress.videoUrl,
                            headers = headers,
                            tempDir = tempDirFile,
                            onChunkProgress = { bytes ->
                                val newTotal = totalDownloaded.addAndGet(bytes)
                                val updatedProgress = progress.copy(
                                    downloadedBytes = newTotal,
                                )
                                onProgress(updatedProgress)
                            },
                        )
                    }
                }

            activeJobs.addAll(chunkJobs)

            // Wait for all chunks to complete
            chunkJobs.joinAll()

            // Check if all chunks completed successfully
            val allComplete = progress.chunks.all { chunk ->
                val chunkFile = File(tempDirFile, chunk.tempFileName)
                if (chunk.endByte < 0) {
                    // Open-ended range - just check that file exists and has some content
                    chunkFile.exists() && chunkFile.length() > 0
                } else {
                    chunkFile.exists() && chunkFile.length() == (chunk.endByte - chunk.startByte + 1)
                }
            }

            if (!allComplete) {
                return DownloadResult.Error(DownloadError.IncompleteDownload("Not all chunks completed"))
            }

            // Merge chunks
            val updatedProgress = progress.copy(
                status = DownloadProgress.Status.COMPLETED,
            )

            val mergeResult = chunkMerger.mergeChunks(
                progress = updatedProgress,
                tempDir = tempDirFile,
                outputFile = outputFileObj,
            )

            when (mergeResult) {
                is ChunkMerger.MergeResult.Success -> {
                    // Clean up progress
                    stateStore.deleteProgress(progress.episodeId)
                    DownloadResult.Success(outputFile)
                }
                is ChunkMerger.MergeResult.Error -> {
                    DownloadResult.Error(DownloadError.MergeError(mergeResult.error.message ?: "Merge failed"))
                }
                ChunkMerger.MergeResult.Cancelled -> {
                    DownloadResult.Cancelled
                }
            }
        } catch (e: CancellationException) {
            // Save progress for resume
            saveProgress(
                progress.copy(
                    downloadedBytes = totalDownloaded.get(),
                    status = DownloadProgress.Status.PAUSED,
                ),
            )
            DownloadResult.Cancelled
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Download failed: ${e.message}" }
            saveProgress(progress.copy(status = DownloadProgress.Status.ERROR))
            DownloadResult.Error(DownloadError.UnknownError(e))
        } finally {
            scope.cancel()
            downloadScope = null
            activeJobs.clear()
        }
    }

    /**
     * Downloads a single chunk with semaphore control.
     */
    private suspend fun downloadChunkWithRetry(
        chunk: ChunkProgress,
        videoUrl: String,
        headers: Headers?,
        tempDir: File,
        onChunkProgress: (Long) -> Unit,
    ) {
        // Acquire semaphore to limit concurrent downloads
        ChunkDownloader.Companion.CONCURRENT_CHUNK_SEMAPHORE.acquire()

        try {
            val tempFile = File(tempDir, chunk.tempFileName)

            val result = chunkDownloader.downloadChunk(
                videoUrl = videoUrl,
                chunk = ChunkRange(chunk.startByte, chunk.endByte),
                headers = headers,
                tempFile = tempFile,
                isFirstChunk = chunk.index == 0,
                onProgress = onChunkProgress,
            )

            when (result) {
                is ChunkDownloader.ChunkDownloadResult.Success -> {
                    // Chunk downloaded successfully
                }
                is ChunkDownloader.ChunkDownloadResult.Error -> {
                    throw IOException("Chunk ${chunk.index} failed: ${result.error}")
                }
                ChunkDownloader.ChunkDownloadResult.Cancelled -> {
                    throw CancellationException("Chunk ${chunk.index} cancelled")
                }
            }
        } finally {
            ChunkDownloader.Companion.CONCURRENT_CHUNK_SEMAPHORE.release()
        }
    }

    /**
     * Saves progress to state store.
     */
    private fun saveProgress(progress: DownloadProgress) {
        stateStore.saveProgress(progress.withUpdatedTimestamp())
    }

    /**
     * Cancels the current download operation.
     */
    fun cancel() {
        downloadScope?.cancel("Download cancelled by user")
        activeJobs.forEach { it.cancel() }
    }

    companion object {
        /**
         * Default number of download threads.
         */
        const val DEFAULT_THREAD_COUNT = 2

        /**
         * Maximum timeout for entire download operation (2 hours).
         */
        const val DOWNLOAD_TIMEOUT_MS = 2L * 60 * 60 * 1000
    }
}

/**
 * Result of a download operation.
 */
sealed class DownloadResult {
    /**
     * Download completed successfully.
     */
    data class Success(
        val file: UniFile,
    ) : DownloadResult()

    /**
     * Download failed with an error.
     */
    data class Error(
        val error: DownloadError,
    ) : DownloadResult()

    /**
     * Download was cancelled.
     */
    data object Cancelled : DownloadResult()
}
