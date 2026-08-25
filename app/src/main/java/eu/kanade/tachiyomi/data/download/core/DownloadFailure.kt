package eu.kanade.tachiyomi.data.download.core

/**
 * The notification that a failure kind produces.
 */
enum class DownloadFailureNotification {
    NONE,
    WARNING,
    ERROR,
}

/**
 * The shape of a download failure.
 *
 * @param fixedCode the error code for this kind, or null to use the cause class name.
 * @param notification the notification that the report point sends for this kind.
 */
enum class DownloadFailureKind(
    val fixedCode: String?,
    val notification: DownloadFailureNotification,
) {
    /**
     * The failure looks like coroutine cancellation and carries no real cause.
     * The caller must decide whether the scope is still active. An active scope
     * means a real failure, so this kind still sends an error notification.
     */
    CANCELLATION(null, DownloadFailureNotification.ERROR),
    STORAGE_PERMISSION(null, DownloadFailureNotification.ERROR),
    RETRIES_EXHAUSTED(null, DownloadFailureNotification.ERROR),
    LOW_STORAGE("LOW_STORAGE", DownloadFailureNotification.WARNING),
    INCOMPLETE_OUTPUT("INCOMPLETE", DownloadFailureNotification.NONE),
    GENERIC(null, DownloadFailureNotification.ERROR),
}

/**
 * One classified download failure.
 *
 * @param kind the failure shape.
 * @param message the raw failure message. The notifier receives this value.
 * @param cause the failure, or null when no throwable exists.
 */
data class DownloadFailure(
    val kind: DownloadFailureKind,
    val message: String?,
    val cause: Throwable?,
) {
    /** The value for `lastErrorCode`. */
    val code: String
        get() = kind.fixedCode ?: cause?.let { it::class.simpleName } ?: "Unknown"

    /** The value for `lastErrorReason`. */
    val reason: String?
        get() = message
}
