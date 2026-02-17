package eu.kanade.tachiyomi.data.download.model

/**
 * Priority levels for downloads.
 * Downloads are ordered by priority first, then by queue position within the same priority.
 */
enum class DownloadPriority(val value: Int) {
    HIGH(2),
    NORMAL(1),
    LOW(0),
}
