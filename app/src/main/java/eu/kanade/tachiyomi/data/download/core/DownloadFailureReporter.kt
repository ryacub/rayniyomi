package eu.kanade.tachiyomi.data.download.core

/**
 * The single point that applies a failure to a download.
 *
 * The reporter writes the error code and the error reason, then sends the
 * notification that the failure kind declares. The reporter does not change the
 * download status and it does not log. The call site keeps those decisions,
 * because they differ per site.
 */
class DownloadFailureReporter<T : DownloadFailureTarget>(
    private val notifyError: (T, DownloadFailure) -> Unit,
    private val notifyWarning: (T, DownloadFailure) -> Unit,
) {

    fun report(download: T, failure: DownloadFailure, reasonFallback: String? = null) {
        download.lastErrorCode = failure.code
        download.lastErrorReason = failure.reason ?: reasonFallback
        when (failure.kind.notification) {
            DownloadFailureNotification.ERROR -> notifyError(download, failure)
            DownloadFailureNotification.WARNING -> notifyWarning(download, failure)
            DownloadFailureNotification.NONE -> Unit
        }
    }

    /** Clears a previous failure. Sites 189 and 582 use this. */
    fun clear(download: T) {
        download.lastErrorCode = null
        download.lastErrorReason = null
    }
}
