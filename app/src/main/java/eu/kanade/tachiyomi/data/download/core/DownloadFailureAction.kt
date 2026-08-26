package eu.kanade.tachiyomi.data.download.core

/**
 * What a catch site must do with one failure.
 *
 * The action carries no side effect. The call site applies it, because the
 * status fields and the log call differ per site.
 */
sealed interface DownloadFailureAction {

    /** Rethrow the original throwable. The scope owner decides. */
    data object Rethrow : DownloadFailureAction

    /**
     * Do nothing. Another site reports this failure.
     * The failure value is carried for future logging; current call sites discard it.
     */
    data class Silence(val failure: DownloadFailure) : DownloadFailureAction

    /** Mark the download failed and report [failure] one time. */
    data class Report(
        val failure: DownloadFailure,
        val reasonFallback: String? = null,
    ) : DownloadFailureAction

    /** Put the download back in the queue for low storage and warn one time. */
    data class PauseLowStorage(val failure: DownloadFailure) : DownloadFailureAction
}
