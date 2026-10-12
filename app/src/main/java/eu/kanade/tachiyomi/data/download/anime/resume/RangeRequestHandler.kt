package eu.kanade.tachiyomi.data.download.anime.resume

import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import logcat.logcat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Handles HTTP range requests for resumable downloads.
 *
 * This class provides functionality to:
 * - Check if a server supports range requests via HEAD request
 * - Create range requests for specific byte ranges
 */
class RangeRequestHandler(
    private val client: OkHttpClient,
) {

    /**
     * Result of checking range request support.
     */
    sealed class RangeSupportResult {
        /**
         * Server supports range requests.
         */
        data class Supported(
            val totalSize: Long,
            val acceptsRanges: String,
        ) : RangeSupportResult()

        /**
         * Server does not support range requests.
         */
        data class NotSupported(
            val reason: String,
        ) : RangeSupportResult()

        /**
         * An error occurred while checking.
         */
        data class Error(
            val exception: Throwable,
        ) : RangeSupportResult()
    }

    /**
     * Checks if the server at the given URL supports HTTP range requests.
     *
     * @param url The URL to check
     * @param headers Optional headers to include in the request
     * @return [RangeSupportResult] indicating whether range requests are supported
     */
    suspend fun checkRangeSupport(
        url: String,
        headers: Headers? = null,
    ): RangeSupportResult {
        val request = Request.Builder()
            .url(url)
            .head()
            .apply {
                headers?.let { headers(it) }
            }
            .build()

        return try {
            val response = client.newCall(request).await()
            response.use {
                when {
                    !response.isSuccessful -> {
                        RangeSupportResult.Error(
                            IOException("HEAD request failed: ${response.code}"),
                        )
                    }
                    else -> parseRangeSupportResponse(response)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Error checking range support for $url: ${e.message}" }
            RangeSupportResult.Error(e)
        }
    }

    /**
     * Parses the response to determine range support.
     */
    private fun parseRangeSupportResponse(response: Response): RangeSupportResult {
        // Check for Accept-Ranges header
        val acceptRanges = response.header("Accept-Ranges")

        // Get content length
        val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L

        return when {
            acceptRanges == null -> {
                // No Accept-Ranges header, server doesn't advertise support
                RangeSupportResult.NotSupported("Server does not advertise range support")
            }
            acceptRanges.equals("none", ignoreCase = true) -> {
                // Explicitly disabled
                RangeSupportResult.NotSupported("Server explicitly disabled range requests")
            }
            contentLength <= 0 -> {
                // No content length, can't do range requests effectively
                RangeSupportResult.NotSupported("Content length unknown or zero")
            }
            else -> {
                RangeSupportResult.Supported(contentLength, acceptRanges)
            }
        }
    }

    /**
     * Creates a GET request for a specific byte range.
     *
     * @param url The URL to download from
     * @param range The byte range to request
     * @param headers Optional headers to include
     * @return A configured [Request]
     */
    fun createRangeRequest(
        url: String,
        range: ChunkRange,
        headers: Headers? = null,
    ): Request {
        return Request.Builder()
            .url(url)
            .apply {
                headers?.let { headers(it) }
            }
            .header("Range", range.toRangeHeader())
            .build()
    }

    companion object {
        /**
         * Maximum number of retry attempts for range validation failures.
         */
        const val MAX_RANGE_VALIDATION_RETRIES = 3
    }
}

/**
 * Extension function to await a Call's response using coroutines.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            // Don't resume if already cancelled
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })

    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (e: Throwable) {
            // Ignore cancellation errors
        }
    }
}
