package eu.kanade.tachiyomi.data.download.anime.multithread

/**
 * Errors that can occur during download.
 */
sealed class DownloadError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class NetworkError(val error: Throwable) : DownloadError(error.message ?: "Network error", error)
    data class Timeout(val msg: String) : DownloadError(msg)
    data class ServerError(val msg: String) : DownloadError(msg)
    data class ClientError(val msg: String) : DownloadError(msg)
    data class InvalidRange(val msg: String) : DownloadError(msg)
    data class InvalidContent(val msg: String) : DownloadError(msg)
    data class DiskFull(val msg: String = "Insufficient disk space") : DownloadError(msg)
    data class MaxRetriesExceeded(val lastError: Throwable) : DownloadError(
        "Max retries exceeded: ${lastError.message}",
        lastError,
    )

    // Multi-thread downloader specific errors
    data class InvalidTempDirectory(val msg: String) : DownloadError(msg)
    data class InvalidOutputFile(val msg: String) : DownloadError(msg)
    data class IncompleteDownload(val msg: String) : DownloadError(msg)
    data class MergeError(val msg: String) : DownloadError(msg)
    data class UnknownError(val error: Throwable) : DownloadError(error.message ?: "Unknown error", error)
}

/**
 * Errors that can occur during merge.
 */
sealed class MergeError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class IncompleteChunks(val msg: String) : MergeError(msg)
    data class ChunkFileMissing(val msg: String) : MergeError(msg)
    data class SizeMismatch(val msg: String) : MergeError(msg)
    data class DiskFull(val msg: String = "Insufficient disk space for merge") : MergeError(msg)
    data class IOError(val error: Throwable) : MergeError(error.message ?: "I/O error during merge", error)
}
