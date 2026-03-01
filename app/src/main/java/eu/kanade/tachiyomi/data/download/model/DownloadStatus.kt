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
 * Minimal read-only status contract consumed by shared UI/status logic.
 */
interface DownloadStatusSnapshot {
    /**
     * True while a transfer request is actively running.
     */
    val isRunningTransfer: Boolean

    /**
     * Current user-facing status.
     */
    val displayStatus: DownloadDisplayStatus

    /**
     * Wall-clock timestamp (ms) for last observed byte progress.
     */
    val lastProgressAt: Long

    /**
     * Current retry attempt count for the active request.
     */
    val retryAttempt: Int

    /**
     * Last human-readable failure reason if one is available.
     */
    val lastErrorReason: String?
}
