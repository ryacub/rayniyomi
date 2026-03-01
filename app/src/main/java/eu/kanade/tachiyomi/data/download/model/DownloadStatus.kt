package eu.kanade.tachiyomi.data.download.model

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

enum class DownloadBlockedReason {
    SLOT,
    NETWORK,
    WIFI,
    STORAGE,
    PREPARING,
    AUTH,
}

interface DownloadStatusSnapshot {
    val isRunningTransfer: Boolean
    val displayStatus: DownloadDisplayStatus
    val lastProgressAt: Long
    val retryAttempt: Int
    val lastErrorReason: String?
}
