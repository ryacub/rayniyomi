package eu.kanade.tachiyomi.data.download.model

/**
 * Unified user-facing runtime status for all download domains.
 */
enum class DownloadDisplayStatus {
    WAITING_FOR_SLOT,
    WAITING_FOR_NETWORK,
    WAITING_FOR_WIFI,
    PREPARING,
    CONNECTING,
    DOWNLOADING,
    STALLED,
    RETRYING,
    PAUSED_BY_USER,
    PAUSED_LOW_STORAGE,
    VERIFYING,
    COMPLETED,
    FAILED,
}

/**
 * Primary blocker category for queued or paused downloads.
 */
enum class DownloadBlockedReason {
    SLOT,
    NETWORK,
    WIFI,
    STORAGE,
    PREPARING,
    AUTH,
}

/**
 * Minimal status contract consumed by shared download logic.
 *
 * The download monitors mutate the progress fields, so the interface exposes
 * them as writable.
 */
interface DownloadStatusSnapshot {
    /**
     * True while a transfer request is actively running.
     */
    val isRunningTransfer: Boolean

    /**
     * Current user-facing status.
     */
    var displayStatus: DownloadDisplayStatus

    /**
     * Wall-clock timestamp (ms) for last observed byte progress.
     */
    var lastProgressAt: Long

    /**
     * Current retry attempt count for the active request.
     */
    var retryAttempt: Int

    /**
     * Last human-readable failure reason if one is available.
     */
    val lastErrorReason: String?
}
