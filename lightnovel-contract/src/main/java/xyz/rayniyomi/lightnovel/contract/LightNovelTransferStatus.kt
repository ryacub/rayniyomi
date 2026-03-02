package xyz.rayniyomi.lightnovel.contract

enum class LightNovelTransferDisplayStatus {
    PREPARING,
    IMPORTING,
    VERIFYING,
    STALLED,
    COMPLETED,
    FAILED,
    PAUSED_LOW_STORAGE,
}

enum class LightNovelTransferBlockedReason {
    STORAGE,
    SOURCE_UNAVAILABLE,
    CORRUPT_FILE,
    UNKNOWN,
}

interface LightNovelTransferSnapshot {
    val displayStatus: LightNovelTransferDisplayStatus
    val lastProgressAt: Long
    val retryAttempt: Int
    val lastErrorReason: String?
    val progressPercent: Int?
}
