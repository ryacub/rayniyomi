package eu.kanade.tachiyomi.data.download.anime.multithread

import eu.kanade.tachiyomi.data.download.anime.resume.ChunkRange
import eu.kanade.tachiyomi.data.download.anime.resume.RangeRequestHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.source
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single chunk of a multi-threaded download.
 *
 * This class handles:
 * - HTTP range requests for specific byte ranges
 * - Retry logic with exponential backoff
 * - Progress reporting
 * - Network error handling
 * - Disk full detection
 * - Timeout handling
 */
class ChunkDownloader(
    private val client: OkHttpClient,
    private val rangeRequestHandler: RangeRequestHandler = RangeRequestHandler(client),
) {
    // A chunk can take minutes on a slow connection. The read timeout still ends a stalled chunk.
    private val chunkClient = client.newBuilder().callTimeout(Duration.ZERO).build()

    /**
     * Result of a chunk download attempt.
     */
    sealed class ChunkDownloadResult {
        /**
         * Chunk was downloaded successfully.
         */
        data class Success(
            val bytesDownloaded: Long,
        ) : ChunkDownloadResult()

        /**
         * Download failed with an error.
         */
        data class Error(
            val error: DownloadError,
        ) : ChunkDownloadResult()

        /**
         * Download was cancelled.
         */
        data object Cancelled : ChunkDownloadResult()
    }

    /**
     * Downloads a single chunk with retry logic.
     *
     * @param videoUrl The URL to download from
     * @param chunk The chunk range to download
     * @param headers Optional headers to include in requests
     * @param tempFile The temporary file to save the chunk to
     * @param isFirstChunk Whether this is the first chunk (index 0), used for video signature validation
     * @param onProgress Callback for progress updates (bytes downloaded in this call)
     * @return [ChunkDownloadResult] indicating success or failure
     */
    suspend fun downloadChunk(
        videoUrl: String,
        chunk: ChunkRange,
        headers: Headers?,
        tempFile: File,
        isFirstChunk: Boolean,
        onProgress: (Long) -> Unit = {},
    ): ChunkDownloadResult {
        var lastError: Throwable? = null
        var lastResult: ChunkDownloadResult? = null

        for (attempt in 1..MAX_RETRIES) {
            // Check for cancellation
            if (!coroutineContext.isActive) {
                return ChunkDownloadResult.Cancelled
            }

            val result = try {
                attemptDownload(videoUrl, chunk, headers, tempFile, isFirstChunk, onProgress)
            } catch (e: CancellationException) {
                throw e // Don't swallow cancellation
            } catch (e: Exception) {
                lastError = e
                logcat(LogPriority.WARN) {
                    "Chunk download attempt $attempt/$MAX_RETRIES failed: ${e.message}"
                }

                if (attempt < MAX_RETRIES) {
                    val delayMs = calculateRetryDelay(attempt)
                    delay(delayMs)
                }

                lastResult = ChunkDownloadResult.Error(DownloadError.NetworkError(e))
                continue
            }

            // Check if the result is a success that we should return immediately
            val checkResult: ChunkDownloadResult = when (result) {
                is ChunkDownloadResult.Success -> result
                is ChunkDownloadResult.Error -> {
                    // Only retry on retryable errors
                    val isRetryable = when (result.error) {
                        is DownloadError.NetworkError -> true
                        is DownloadError.Timeout -> true
                        is DownloadError.ServerError -> true
                        else -> false
                    }

                    if (isRetryable && attempt < MAX_RETRIES) {
                        lastError = result.error as? Throwable ?: Exception(result.error.toString())
                        lastResult = result
                        val delayMs = calculateRetryDelay(attempt)
                        delay(delayMs)
                        // Continue to next attempt
                        continue
                    } else {
                        // Non-retryable error or max retries exceeded
                        result
                    }
                }
                ChunkDownloadResult.Cancelled -> result
            }
            return checkResult
        }

        // If we exit the loop without returning, return the last error
        return lastResult ?: ChunkDownloadResult.Error(
            DownloadError.MaxRetriesExceeded(
                lastError ?: Exception("Unknown error"),
            ),
        )
    }

    /**
     * Single attempt to download a chunk.
     */
    private suspend fun attemptDownload(
        videoUrl: String,
        chunk: ChunkRange,
        headers: Headers?,
        tempFile: File,
        isFirstChunk: Boolean,
        onProgress: (Long) -> Unit,
    ): ChunkDownloadResult = coroutineScope {
        val keptBytes = keptBytes(chunk, tempFile)
        if (keptBytes == chunk.size) return@coroutineScope validatedResult(tempFile, isFirstChunk, 0L)

        val missingRange = ChunkRange(chunk.startByte + keptBytes, chunk.endByte)
        val call = chunkClient.newCall(rangeRequestHandler.createRangeRequest(videoUrl, missingRange, headers))
        // A blocking socket read ignores coroutine cancellation, so close the call instead.
        val callCanceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            downloadResponse(call, chunk, tempFile, keptBytes, isFirstChunk, onProgress)
        } finally {
            callCanceller.cancel()
        }
    }

    private suspend fun downloadResponse(
        call: Call,
        chunk: ChunkRange,
        tempFile: File,
        keptBytes: Long,
        isFirstChunk: Boolean,
        onProgress: (Long) -> Unit,
    ): ChunkDownloadResult {
        return withContext(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    responseResult(response, chunk, tempFile, keptBytes, isFirstChunk, onProgress)
                }
            } catch (e: IOException) {
                ChunkDownloadResult.Error(e.toDownloadError())
            }
        }
    }

    private fun responseResult(
        response: Response,
        chunk: ChunkRange,
        tempFile: File,
        keptBytes: Long,
        isFirstChunk: Boolean,
        onProgress: (Long) -> Unit,
    ): ChunkDownloadResult {
        return when {
            response.code == HttpURLConnection.HTTP_PARTIAL -> {
                handleSuccessResponse(response, tempFile, keptBytes, isFirstChunk, onProgress)
            }
            response.code == HttpURLConnection.HTTP_OK -> {
                // Server ignored range request, got full file instead of chunk
                // This is fatal for multi-threaded downloads - each chunk would get full file
                logcat(LogPriority.ERROR) {
                    "Server ignored range request, received full file instead of chunk"
                }
                ChunkDownloadResult.Error(
                    DownloadError.InvalidRange(
                        "Server does not support range requests; multi-threaded download not possible",
                    ),
                )
            }
            response.code == HTTP_RANGE_NOT_SATISFIABLE -> {
                // 416 Range Not Satisfiable
                ChunkDownloadResult.Error(
                    DownloadError.InvalidRange("Range $chunk is not satisfiable"),
                )
            }
            response.code >= 500 -> {
                // Server error, retryable
                ChunkDownloadResult.Error(
                    DownloadError.ServerError("Server error: ${response.code}"),
                )
            }
            response.code >= 400 -> {
                // Client error, not retryable
                ChunkDownloadResult.Error(
                    DownloadError.ClientError("Client error: ${response.code}"),
                )
            }
            else -> {
                ChunkDownloadResult.Error(
                    DownloadError.NetworkError(IOException("Unexpected response code: ${response.code}")),
                )
            }
        }
    }

    /**
     * Writes the response body into the chunk file after the bytes that it already holds.
     */
    private fun handleSuccessResponse(
        response: Response,
        tempFile: File,
        keptBytes: Long,
        isFirstChunk: Boolean,
        onProgress: (Long) -> Unit,
    ): ChunkDownloadResult {
        val body = response.body ?: return ChunkDownloadResult.Error(
            DownloadError.NetworkError(IOException("Empty response body")),
        )

        var totalBytes = 0L
        tempFile.parentFile?.mkdirs()

        // A positional write keeps each byte at its own offset, even if a cancelled writer still finishes a write.
        RandomAccessFile(tempFile, "rw").use { file ->
            if (keptBytes == 0L) file.setLength(0)
            file.seek(keptBytes)
            val source = body.source()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int

            while (source.read(buffer).also { bytesRead = it } != -1) {
                file.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                onProgress(bytesRead.toLong())
            }
        }

        return validatedResult(tempFile, isFirstChunk, totalBytes)
    }

    private fun validatedResult(tempFile: File, isFirstChunk: Boolean, bytesDownloaded: Long): ChunkDownloadResult {
        if (isFirstChunk && !VideoSignatureValidator.validateVideoSignature(tempFile, isFirstChunk)) {
            return ChunkDownloadResult.Error(
                DownloadError.InvalidContent("Downloaded content is not valid video"),
            )
        }
        return ChunkDownloadResult.Success(bytesDownloaded)
    }

    private fun IOException.toDownloadError(): DownloadError = when {
        this is SocketTimeoutException -> DownloadError.Timeout(message ?: "Chunk read timed out")
        message?.contains("ENOSPC") == true || message?.contains("No space left") == true -> DownloadError.DiskFull()
        else -> DownloadError.NetworkError(this)
    }

    /**
     * Calculates exponential backoff delay.
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val baseDelay = 1000L // 1 second
        val exponentialDelay = baseDelay * (1 shl (attempt - 1))
        return exponentialDelay.coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    companion object {
        // The file holds the start of the chunk, written in order, so the next attempt continues from its end.
        // An open-ended chunk has no fixed size to check the file against, so it starts again.
        internal fun keptBytes(chunk: ChunkRange, tempFile: File): Long =
            if (chunk.endByte < 0) 0L else tempFile.length().coerceAtMost(chunk.size)

        /**
         * Maximum number of retry attempts.
         */
        const val MAX_RETRIES = 3

        /**
         * Maximum retry delay in milliseconds.
         */
        const val MAX_RETRY_DELAY_MS = 10000L // 10 seconds

        /**
         * Global semaphore to limit concurrent chunk downloads.
         */
        val CONCURRENT_CHUNK_SEMAPHORE = Semaphore(4)

        /**
         * HTTP 416 Range Not Satisfiable
         */
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}
