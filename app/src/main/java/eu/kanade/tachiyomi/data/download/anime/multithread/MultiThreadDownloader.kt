package eu.kanade.tachiyomi.data.download.anime.multithread

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkProgress
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkRange
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkSizeCalculator
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadProgress
import eu.kanade.tachiyomi.data.download.anime.resume.DownloadStateStore
import eu.kanade.tachiyomi.data.download.anime.resume.RangeRequestHandler
import eu.kanade.tachiyomi.data.download.model.DownloadRecovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import logcat.LogPriority
import logcat.logcat
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.io.File
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
    private val chunkDownloader: ChunkDownloader = ChunkDownloader(client),
) {
    private val rangeRequestHandler = RangeRequestHandler(client)
    private val chunkMerger = ChunkMerger()

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
        onRecovery: (DownloadRecovery) -> Unit = {},
        onProgress: (DownloadProgress) -> Unit = {},
    ): DownloadResult {
        val rangeSupport = when (val result = rangeRequestHandler.checkRangeSupport(videoUrl, headers)) {
            is RangeRequestHandler.RangeSupportResult.Supported -> result
            is RangeRequestHandler.RangeSupportResult.Error -> {
                return DownloadResult.Error(DownloadError.UnknownError(result.exception))
            }
            is RangeRequestHandler.RangeSupportResult.NotSupported -> {
                return DownloadResult.Error(DownloadError.InvalidRange(result.reason))
            }
        }
        val existingProgress = stateStore.loadProgressIfMatching(episodeId, videoUrl)
        val entityTag = rangeSupport.entityTag
            ?: return DownloadResult.Error(DownloadError.InvalidRange("Server has no strong video validator"))
        val transferHeaders = (headers ?: Headers.Builder().build()).newBuilder().set("If-Range", entityTag).build()

        return if (existingProgress != null &&
            existingProgress.status != DownloadProgress.Status.COMPLETED &&
            existingProgress.entityTag == entityTag &&
            existingProgress.resourceUrl == rangeSupport.resourceUrl &&
            existingProgress.totalBytes == rangeSupport.totalSize
        ) {
            logcat(LogPriority.INFO) { "Resuming download for episode $episodeId" }
            val chunkDir = tmpDir.filePath?.let(::File)
            if (chunkDir != null) {
                onRecovery(recoveryFor(existingProgress, chunkDir))
            }
            resume(existingProgress, transferHeaders, tmpDir, outputFile, onProgress)
        } else {
            if (existingProgress != null ||
                tmpDir.filePath?.let(::File)?.list().orEmpty().any { it.startsWith("chunk_${episodeId}_") }
            ) {
                onRecovery(DownloadRecovery.RESTART)
            }
            startNewDownload(episodeId, videoUrl, transferHeaders, tmpDir, outputFile, onProgress, rangeSupport)
        }
    }

    internal fun hasCheckpoint(episodeId: Long, url: String): Boolean =
        stateStore.loadProgress(episodeId)?.let { it.videoUrl == url && it.entityTag != null } == true

    internal fun discardStagedChunks(episodeId: Long, directory: UniFile) {
        val names = Regex("chunk_${episodeId}_[0-9]+\\.tmp")
        directory.listFiles().orEmpty().forEach { file ->
            if (names.matches(file.name.orEmpty())) {
                check(file.delete()) { "Cannot remove an invalid video chunk" }
            }
        }
        stateStore.deleteProgress(episodeId)
    }

    private fun recoveryFor(progress: DownloadProgress, directory: File): DownloadRecovery {
        var hasSavedBytes = false
        for (chunk in progress.chunks) {
            val file = File(directory, chunk.tempFileName)
            val size = file.length()
            if (size > chunk.totalBytes || size < chunk.downloadedBytes) return DownloadRecovery.REPAIR
            if (size > 0) hasSavedBytes = true
        }
        return if (hasSavedBytes) DownloadRecovery.RESUME else DownloadRecovery.RESTART
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
        val chunkDir = tmpDir.filePath?.let(::File)
        val verifiedProgress = progress.copy(
            chunks = progress.chunks.map { chunk ->
                val savedBytes = chunkDir?.let { File(it, chunk.tempFileName).length() }
                if (chunk.isComplete && savedBytes != chunk.totalBytes) {
                    chunk.copy(downloadedBytes = 0, status = ChunkProgress.ChunkStatus.PENDING)
                } else {
                    chunk
                }
            },
        )
        return performDownload(verifiedProgress, headers, tmpDir, outputFile, onProgress, resume = true)
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
        rangeSupport: RangeRequestHandler.RangeSupportResult.Supported,
    ): DownloadResult {
        val totalBytes = rangeSupport.totalSize

        // Get current thread count from provider (respects runtime preference changes)
        val maxThreads = maxThreadsProvider()

        // Calculate chunks
        val chunks = ChunkSizeCalculator.calculateChunks(totalBytes, maxThreads)

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
            entityTag = rangeSupport.entityTag,
            resourceUrl = rangeSupport.resourceUrl,
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
        // Get temp directory as File
        val tempDirFile = File(
            tmpDir.filePath ?: return DownloadResult.Error(
                DownloadError.InvalidTempDirectory("Cannot access temp directory"),
            ),
        )

        // A file left by another download of this episode can hold other bytes.
        if (!resume) {
            for (chunk in progress.chunks) {
                val file = File(tempDirFile, chunk.tempFileName)
                if (file.exists() && !file.delete()) {
                    return DownloadResult.Error(DownloadError.InvalidTempDirectory("Cannot clear stale chunk"))
                }
            }
            if (!stateStore.checkpoint(progress)) {
                return DownloadResult.Error(DownloadError.InvalidTempDirectory("Cannot save the video checkpoint"))
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        val chunkState = ChunkStateTracker(progress.chunks) { chunk ->
            ChunkDownloader.keptBytes(ChunkRange(chunk.startByte, chunk.endByte), File(tempDirFile, chunk.tempFileName))
        }

        return try {
            // Each job returns its result. A failed child of a supervisor scope does not fail its siblings.
            val chunkJobs = progress.chunks
                .filter { !it.isComplete }
                .map { chunk ->
                    scope.async {
                        downloadChunkWithRetry(
                            chunk = chunk,
                            progress = progress,
                            headers = headers,
                            tempDir = tempDirFile,
                            onChunkProgress = { bytes ->
                                chunkState.addBytes(chunk.index, bytes)
                                onProgress(chunkState.applyTo(progress))
                            },
                        )
                    }
                }

            val chunkResults = chunkJobs.awaitAll()

            if (chunkResults.any { it === ChunkDownloader.ChunkDownloadResult.Cancelled }) {
                throw CancellationException("Chunk cancelled")
            }
            chunkResults.firstFailure()?.let { error ->
                logcat(LogPriority.ERROR) { "Chunk download failed: ${error.message}" }
                stateStore.saveProgress(chunkState.applyTo(progress).copy(status = DownloadProgress.Status.ERROR))
                return DownloadResult.Error(error)
            }

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

            val updatedProgress = chunkState.applyTo(progress).copy(
                status = DownloadProgress.Status.COMPLETED,
            )

            val mergeResult = chunkMerger.mergeChunks(
                progress = updatedProgress,
                tempDir = tempDirFile,
                outputFile = outputFile,
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
            stateStore.saveProgress(
                progress.copy(
                    downloadedBytes = chunkState.totalBytes(),
                    status = DownloadProgress.Status.PAUSED,
                ),
            )
            DownloadResult.Cancelled
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Download failed: ${e.message}" }
            stateStore.saveProgress(progress.copy(status = DownloadProgress.Status.ERROR))
            DownloadResult.Error(DownloadError.UnknownError(e))
        } finally {
            scope.cancel()
        }
    }

    /**
     * Downloads a single chunk with semaphore control.
     */
    private suspend fun downloadChunkWithRetry(
        chunk: ChunkProgress,
        progress: DownloadProgress,
        headers: Headers?,
        tempDir: File,
        onChunkProgress: (Long) -> Unit,
    ): ChunkDownloader.ChunkDownloadResult {
        // Acquire semaphore to limit concurrent downloads
        ChunkDownloader.Companion.CONCURRENT_CHUNK_SEMAPHORE.acquire()

        try {
            val tempFile = File(tempDir, chunk.tempFileName)

            return chunkDownloader.downloadChunk(
                videoUrl = progress.videoUrl,
                chunk = ChunkRange(chunk.startByte, chunk.endByte),
                headers = headers,
                tempFile = tempFile,
                isFirstChunk = chunk.index == 0,
                onProgress = onChunkProgress,
                identity = progress.resourceUrl?.let { resource ->
                    progress.entityTag?.let { tag -> VideoRangeIdentity(resource, tag, progress.totalBytes) }
                },
            )
        } finally {
            ChunkDownloader.Companion.CONCURRENT_CHUNK_SEMAPHORE.release()
        }
    }

    // A full disk outranks a range error, because the caller must pause for it.
    private fun List<ChunkDownloader.ChunkDownloadResult>.firstFailure(): DownloadError? {
        val errors = filterIsInstance<ChunkDownloader.ChunkDownloadResult.Error>().map { it.error }
        return errors.firstOrNull { it is DownloadError.DiskFull }
            ?: errors.firstOrNull { it is DownloadError.InvalidRange }
            ?: errors.firstOrNull()
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

// An incomplete chunk starts from the bytes in its temp file, because the download continues from there.
private class ChunkStateTracker(chunks: List<ChunkProgress>, keptBytes: (ChunkProgress) -> Long) {
    private val bytesByIndex = chunks.associate { chunk ->
        chunk.index to AtomicLong(if (chunk.isComplete) chunk.downloadedBytes else keptBytes(chunk))
    }

    fun addBytes(index: Int, bytes: Long) {
        bytesByIndex.getValue(index).addAndGet(bytes)
    }

    fun totalBytes(): Long = bytesByIndex.values.sumOf { it.get() }

    fun applyTo(progress: DownloadProgress): DownloadProgress = progress.copy(
        downloadedBytes = totalBytes(),
        chunks = progress.chunks.map { chunk ->
            val updated = chunk.copy(downloadedBytes = bytesByIndex.getValue(chunk.index).get())
            if (updated.isComplete) updated.copy(status = ChunkProgress.ChunkStatus.COMPLETED) else updated
        },
    )
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
