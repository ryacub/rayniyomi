package eu.kanade.tachiyomi.data.download.anime.resume

import kotlinx.serialization.Serializable

/**
 * Represents the progress of a resumable download.
 * This is persisted to allow recovery after app restarts or network interruptions.
 *
 * @property episodeId The ID of the episode being downloaded
 * @property videoUrl The URL of the video being downloaded
 * @property totalBytes The total size of the file in bytes (-1 if unknown)
 * @property downloadedBytes The total number of bytes downloaded so far
 * @property chunks The list of chunk progress items for multi-threaded downloads
 * @property status The current status of the download
 * @property createdAt Timestamp when the download was first started
 * @property updatedAt Timestamp of the last update
 */
@Serializable
data class DownloadProgress(
    val episodeId: Long,
    val videoUrl: String,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val chunks: List<ChunkProgress> = emptyList(),
    val status: Status = Status.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    enum class Status {
        IN_PROGRESS,
        PAUSED,
        ERROR,
        COMPLETED,
    }

    /**
     * Returns the overall download progress as a percentage (0-100).
     * Returns -1 if total size is unknown.
     */
    val progressPercent: Int
        get() = if (totalBytes > 0) {
            (100 * downloadedBytes / totalBytes).toInt()
        } else {
            -1
        }

    /**
     * Returns true if all chunks are completed.
     */
    val isComplete: Boolean
        get() = chunks.all { it.isComplete }

    /**
     * Returns a copy with updated timestamp.
     */
    fun withUpdatedTimestamp(): DownloadProgress =
        copy(updatedAt = System.currentTimeMillis())
}

/**
 * Represents the progress of a single chunk in a multi-threaded download.
 *
 * @property index The chunk index (0-based)
 * @property startByte The starting byte position (inclusive)
 * @property endByte The ending byte position (inclusive)
 * @property downloadedBytes The number of bytes downloaded for this chunk
 * @property status The current status of this chunk
 * @property tempFileName The name of the temporary file for this chunk
 */
@Serializable
data class ChunkProgress(
    val index: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val tempFileName: String,
) {
    enum class ChunkStatus {
        PENDING,
        DOWNLOADING,
        COMPLETED,
        ERROR,
    }

    /**
     * The expected total size of this chunk.
     * Returns -1 if endByte is -1 (open-ended range).
     */
    val totalBytes: Long
        get() = if (endByte < 0) -1 else endByte - startByte + 1

    /**
     * Returns true if this chunk is fully downloaded.
     * For open-ended ranges (endByte = -1), returns false since we can't determine completion.
     */
    val isComplete: Boolean
        get() = if (endByte < 0) false else downloadedBytes >= totalBytes

    /**
     * Returns the progress of this chunk as a percentage (0-100).
     */
    val progressPercent: Int
        get() = if (totalBytes > 0) {
            (100 * downloadedBytes / totalBytes).toInt()
        } else {
            0
        }
}

/**
 * Represents a byte range for a download chunk.
 *
 * @param startByte The starting byte position (inclusive)
 * @param endByte The ending byte position (inclusive), or -1 to indicate "to the end"
 */
data class ChunkRange(
    val startByte: Long,
    val endByte: Long,
) {
    /**
     * The size of this range in bytes.
     * Returns -1 if endByte is -1 (open-ended range).
     */
    val size: Long
        get() = if (endByte < 0) -1 else endByte - startByte + 1

    /**
     * Converts to an HTTP Range header value (e.g., "bytes=0-1023" or "bytes=0-" for open-ended).
     */
    fun toRangeHeader(): String = if (endByte < 0) {
        "bytes=$startByte-"
    } else {
        "bytes=$startByte-$endByte"
    }
}
