package xyz.rayniyomi.lightnovel.contract

/**
 * User-visible lifecycle states for light novel transfer/import operations.
 */
enum class LightNovelTransferDisplayStatus {
    /** Import request accepted and setup/validation is in progress. */
    PREPARING,

    /** Bytes are currently being copied from source to destination. */
    IMPORTING,

    /** Byte copy finished and EPUB contents are being validated/parsed. */
    VERIFYING,

    /** Transfer has an active request but no byte progress in the stall window. */
    STALLED,

    /** Import finished successfully and the book was persisted. */
    COMPLETED,

    /** Import failed and requires user intervention or retry. */
    FAILED,

    /** Import cannot continue because device storage is insufficient. */
    PAUSED_LOW_STORAGE,
}

/**
 * Normalized blocker/failure categories for light novel transfer reporting.
 */
enum class LightNovelTransferBlockedReason {
    /** Local storage is insufficient for current transfer step. */
    STORAGE,

    /** Source URI or permission is unavailable. */
    SOURCE_UNAVAILABLE,

    /** Source content is malformed or unreadable as EPUB. */
    CORRUPT_FILE,

    /** Fallback category when a specific blocker is unavailable. */
    UNKNOWN,
}

/**
 * Read-only snapshot consumed by UI and notifier layers.
 */
interface LightNovelTransferSnapshot {
    val displayStatus: LightNovelTransferDisplayStatus
    val lastProgressAt: Long
    val retryAttempt: Int
    val lastErrorReason: String?
    val progressPercent: Int?
}
