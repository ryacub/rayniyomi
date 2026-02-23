package eu.kanade.tachiyomi.data.download.anime.strategy

import eu.kanade.tachiyomi.data.download.anime.multithread.VideoFormat
import eu.kanade.tachiyomi.data.download.anime.multithread.VideoSignatureValidator
import eu.kanade.tachiyomi.data.download.anime.resume.ChunkSizeCalculator
import eu.kanade.tachiyomi.data.download.anime.resume.RangeRequestHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import logcat.LogPriority
import logcat.logcat
import okhttp3.OkHttpClient

/**
 * Selects the appropriate download strategy based on video characteristics.
 *
 * Strategy selection criteria:
 * - HLS/DASH streams: Always use FFmpeg (streaming protocols)
 * - Small files (< 5MB): Use single-threaded download
 * - MP4/MKV/WebM with range support: Use multi-threaded download
 * - MP4/MKV without range support: Use single-threaded download
 * - Unknown formats: Fall back to FFmpeg
 */
class DownloadStrategySelector(
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Result of strategy selection.
     */
    sealed class StrategyResult {
        /**
         * Strategy was successfully determined.
         */
        data class Success(
            val strategy: DownloadStrategy,
            val totalSize: Long = -1,
        ) : StrategyResult()

        /**
         * Strategy selection failed.
         */
        data class Error(
            val reason: String,
        ) : StrategyResult()
    }

    /**
     * Determines the best download strategy for a video URL.
     *
     * This performs:
     * 1. Format detection from URL
     * 2. For MP4/MKV: Range support check via HEAD request
     * 3. Size check to decide single vs multi-thread
     *
     * @param videoUrl The URL to analyze
     * @param headers Optional headers for the request
     * @param multiThreadEnabled Whether multi-thread downloads are enabled in settings
     * @param maxConnections Maximum number of connections allowed (1-4)
     * @return [StrategyResult] containing the selected strategy
     */
    suspend fun selectStrategy(
        videoUrl: String,
        headers: okhttp3.Headers? = null,
        multiThreadEnabled: Boolean = true,
        maxConnections: Int = ChunkSizeCalculator.MAX_CHUNKS,
    ): StrategyResult {
        // Step 1: Detect format from URL
        val format = VideoSignatureValidator.detectVideoFormat(videoUrl)
        logcat(LogPriority.DEBUG) { "Detected video format for strategy: $format" }

        return when (format) {
            VideoFormat.HLS,
            VideoFormat.DASH,
            -> {
                // Streaming protocols always need FFmpeg
                StrategyResult.Success(DownloadStrategy.FFMPEG)
            }

            VideoFormat.MP4,
            VideoFormat.MKV,
            VideoFormat.WEBM,
            VideoFormat.AVI,
            -> {
                // Check for multi-thread eligibility
                if (!multiThreadEnabled) {
                    return StrategyResult.Success(DownloadStrategy.SINGLE_THREAD)
                }

                // Check range support
                checkRangeAndSelectStrategy(videoUrl, headers, maxConnections)
            }

            VideoFormat.MPEG_TS -> {
                // MPEG-TS segments are typically small, use single thread
                StrategyResult.Success(DownloadStrategy.SINGLE_THREAD)
            }

            VideoFormat.UNKNOWN -> {
                // Unknown format, fall back to FFmpeg for safety
                logcat(LogPriority.WARN) { "Unknown video format, falling back to FFmpeg" }
                StrategyResult.Success(DownloadStrategy.FFMPEG)
            }
        }
    }

    /**
     * Checks range support and selects appropriate strategy.
     */
    private suspend fun checkRangeAndSelectStrategy(
        videoUrl: String,
        headers: okhttp3.Headers?,
        maxConnections: Int,
    ): StrategyResult {
        val rangeHandler = RangeRequestHandler(client)
        val rangeResult = rangeHandler.checkRangeSupport(videoUrl, headers)

        return when (rangeResult) {
            is RangeRequestHandler.RangeSupportResult.Supported -> {
                val totalSize = rangeResult.totalSize

                // Check if file is large enough for multi-thread
                if (ChunkSizeCalculator.shouldUseMultiThread(totalSize)) {
                    val threadCount = ChunkSizeCalculator.getRecommendedThreadCount(totalSize, maxConnections)
                    StrategyResult.Success(
                        strategy = DownloadStrategy.MULTI_THREAD,
                        totalSize = totalSize,
                    )
                } else {
                    // Small file, use single thread
                    StrategyResult.Success(
                        strategy = DownloadStrategy.SINGLE_THREAD,
                        totalSize = totalSize,
                    )
                }
            }
            is RangeRequestHandler.RangeSupportResult.NotSupported -> {
                logcat(LogPriority.INFO) {
                    "Range requests not supported: ${rangeResult.reason}"
                }
                StrategyResult.Success(DownloadStrategy.SINGLE_THREAD)
            }
            is RangeRequestHandler.RangeSupportResult.Error -> {
                logcat(LogPriority.ERROR) {
                    "Error checking range support: ${rangeResult.exception.message}"
                }
                // Fall back to single thread on error
                StrategyResult.Success(DownloadStrategy.SINGLE_THREAD)
            }
        }
    }

    /**
     * Quick format check without network request.
     * Use this when you only need to know the format type.
     */
    fun detectFormatOnly(videoUrl: String): VideoFormat {
        return VideoSignatureValidator.detectVideoFormat(videoUrl)
    }

    /**
     * Checks if a format supports multi-threaded downloading.
     */
    fun supportsMultiThread(format: VideoFormat): Boolean {
        return VideoSignatureValidator.supportsMultiThread(format)
    }
}

/**
 * Available download strategies.
 */
enum class DownloadStrategy {
    /**
     * Multi-threaded download using HTTP range requests.
     * Best for large MP4/MKV files on servers that support ranges.
     */
    MULTI_THREAD,

    /**
     * Single-threaded sequential download.
     * Used for small files or servers without range support.
     */
    SINGLE_THREAD,

    /**
     * FFmpeg-based download for streaming protocols.
     * Required for HLS/DASH and some complex media files.
     */
    FFMPEG,
}
