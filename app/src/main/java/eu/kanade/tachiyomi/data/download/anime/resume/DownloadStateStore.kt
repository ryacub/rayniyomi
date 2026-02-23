package eu.kanade.tachiyomi.data.download.anime.resume

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists download progress to disk using Protocol Buffers.
 *
 * This allows downloads to be resumed after:
 * - App restarts
 * - Process death
 * - Network interruptions
 *
 * Progress files are stored in the app's cache directory and are cleaned up
 * when downloads complete or become stale (>7 days old).
 */
class DownloadStateStore(
    context: Context,
) {
    /**
     * Directory where progress files are stored.
     */
    private val progressDir = File(context.cacheDir, PROGRESS_DIR_NAME).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    /**
     * In-memory cache of progress entries to reduce disk I/O.
     */
    private val memoryCache = ConcurrentHashMap<Long, DownloadProgress>()

    /**
     * ProtoBuf instance for serialization.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val protoBuf = ProtoBuf

    /**
     * Saves download progress to disk.
     *
     * @param progress The progress to save
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun saveProgress(progress: DownloadProgress) {
        try {
            memoryCache[progress.episodeId] = progress

            val file = getProgressFile(progress.episodeId)
            val bytes = protoBuf.encodeToByteArray(progress)
            file.writeBytes(bytes)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Failed to save download progress for episode ${progress.episodeId}"
            }
        }
    }

    /**
     * Loads download progress from disk.
     *
     * @param episodeId The episode ID to load progress for
     * @return The saved progress, or null if not found
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun loadProgress(episodeId: Long): DownloadProgress? {
        // Check memory cache first
        memoryCache[episodeId]?.let { return it }

        return try {
            val file = getProgressFile(episodeId)
            if (!file.exists()) {
                return null
            }

            val bytes = file.readBytes()
            val progress = protoBuf.decodeFromByteArray<DownloadProgress>(bytes)

            // Cache in memory
            memoryCache[episodeId] = progress
            progress
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to load download progress for episode $episodeId: ${e.message}" }
            null
        }
    }

    /**
     * Loads download progress for a specific video URL.
     * Returns null if the URL doesn't match (e.g., video URL changed).
     *
     * @param episodeId The episode ID
     * @param videoUrl The expected video URL
     * @return The saved progress if URLs match, null otherwise
     */
    fun loadProgressIfMatching(episodeId: Long, videoUrl: String): DownloadProgress? {
        val progress = loadProgress(episodeId) ?: return null

        // Verify the video URL matches (in case the source changed)
        return if (progress.videoUrl == videoUrl) {
            progress
        } else {
            logcat(LogPriority.INFO) {
                "Video URL mismatch for episode $episodeId, discarding old progress"
            }
            deleteProgress(episodeId)
            null
        }
    }

    /**
     * Deletes download progress from disk and memory cache.
     *
     * @param episodeId The episode ID to delete
     */
    fun deleteProgress(episodeId: Long) {
        memoryCache.remove(episodeId)

        try {
            val file = getProgressFile(episodeId)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to delete progress for episode $episodeId: ${e.message}" }
        }
    }

    /**
     * Lists all saved progress entries.
     *
     * @return List of all saved download progress
     */
    fun listAllProgress(): List<DownloadProgress> {
        return progressDir.listFiles { file ->
            file.extension == PROGRESS_FILE_EXTENSION
        }?.mapNotNull { file ->
            try {
                val episodeId = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
                loadProgress(episodeId)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to load progress from ${file.name}: ${e.message}" }
                null
            }
        } ?: emptyList()
    }

    /**
     * Cleans up stale progress entries that haven't been updated in the specified time.
     *
     * @param maxAgeMs Maximum age in milliseconds (default: 7 days)
     * @return Number of entries cleaned up
     */
    fun cleanupStaleEntries(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Int {
        val now = System.currentTimeMillis()
        var cleanedCount = 0

        val entries = listAllProgress()
        for (progress in entries) {
            if (now - progress.updatedAt > maxAgeMs) {
                deleteProgress(progress.episodeId)
                cleanedCount++
            }
        }

        if (cleanedCount > 0) {
            logcat(LogPriority.INFO) { "Cleaned up $cleanedCount stale download progress entries" }
        }

        return cleanedCount
    }

    /**
     * Clears all completed downloads from storage.
     *
     * @return Number of entries cleared
     */
    fun clearCompletedDownloads(): Int {
        var clearedCount = 0

        val entries = listAllProgress()
        for (progress in entries) {
            if (progress.status == DownloadProgress.Status.COMPLETED) {
                deleteProgress(progress.episodeId)
                clearedCount++
            }
        }

        return clearedCount
    }

    /**
     * Gets the file path for a progress entry.
     */
    private fun getProgressFile(episodeId: Long): File {
        return File(progressDir, "$episodeId.$PROGRESS_FILE_EXTENSION")
    }

    companion object {
        private const val PROGRESS_DIR_NAME = "download_progress"
        private const val PROGRESS_FILE_EXTENSION = "dp"

        /**
         * Default maximum age for progress entries: 7 days
         */
        private const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
