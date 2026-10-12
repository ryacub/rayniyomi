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
 * Progress files are stored in the app's cache directory. A file is deleted
 * when its download completes, leaves the queue, or gets a new video URL.
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
     * Gets the file path for a progress entry.
     */
    private fun getProgressFile(episodeId: Long): File {
        return File(progressDir, "$episodeId.$PROGRESS_FILE_EXTENSION")
    }

    companion object {
        private const val PROGRESS_DIR_NAME = "download_progress"
        private const val PROGRESS_FILE_EXTENSION = "dp"
    }
}
