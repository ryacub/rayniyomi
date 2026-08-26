package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CancellationException

/**
 * The named failure policies that the manga downloader and the anime downloader
 * share. Each function maps one throwable to exactly one action. No function
 * writes to a download and no function sends a notification.
 */
object DownloadFailurePolicy {

    /**
     * The policy for the outer download job.
     *
     * A cancellation-shaped throwable is a real failure while the scope is
     * still active. A cancellation on a dead scope belongs to the scope owner.
     */
    fun forDownloadJob(error: Throwable, scopeActive: Boolean): DownloadFailureAction {
        val failure = DownloadFailureClassifier.classify(error)
        if (failure.kind == DownloadFailureKind.CANCELLATION && !scopeActive) {
            return DownloadFailureAction.Rethrow
        }
        return DownloadFailureAction.Report(failure, reasonFallback = failure.code)
    }

    /**
     * The policy for one queue item: one episode or one chapter.
     *
     * Cancellation goes to the job policy unchanged. A low storage failure is
     * already reported by the site that raised it.
     */
    fun forItem(error: Throwable): DownloadFailureAction = when (error) {
        is CancellationException -> DownloadFailureAction.Rethrow
        is LowStorageException -> DownloadFailureAction.Silence(
            DownloadFailure(DownloadFailureKind.LOW_STORAGE, error.message, error),
        )
        else -> DownloadFailureAction.Report(DownloadFailureClassifier.classify(error))
    }

    /**
     * The policy for the video fetch. Low storage pauses here, because the
     * fetch returns a result instead of throwing.
     */
    fun forVideoFetch(error: Throwable): DownloadFailureAction = when (error) {
        is CancellationException -> DownloadFailureAction.Rethrow
        is LowStorageException -> DownloadFailureAction.PauseLowStorage(
            DownloadFailure(DownloadFailureKind.LOW_STORAGE, error.message, error),
        )
        // The retry routine made this failure. The item policy reports it one time.
        is StoragePermissionException -> DownloadFailureAction.Rethrow
        is RetriesExhaustedException -> DownloadFailureAction.Rethrow
        else -> DownloadFailureAction.Report(DownloadFailureClassifier.classify(error))
    }

    /**
     * The policy for the image fetch. Low storage goes up to the item policy,
     * because the image download site already reported it.
     */
    fun forImageFetch(error: Throwable): DownloadFailureAction = when (error) {
        is CancellationException -> DownloadFailureAction.Rethrow
        is LowStorageException -> DownloadFailureAction.Rethrow
        is StoragePermissionException -> DownloadFailureAction.Rethrow
        is RetriesExhaustedException -> DownloadFailureAction.Rethrow
        else -> DownloadFailureAction.Report(DownloadFailureClassifier.classify(error))
    }

    /** The failure that a free-space, ffmpeg output, or image save check produces. */
    fun lowStorageFailure(message: String?, cause: Throwable? = null): DownloadFailure =
        DownloadFailure(DownloadFailureKind.LOW_STORAGE, message, cause)

    /** The failure that an incomplete download output produces. */
    fun incompleteOutputFailure(message: String): DownloadFailure =
        DownloadFailure(DownloadFailureKind.INCOMPLETE_OUTPUT, message, null)
}
