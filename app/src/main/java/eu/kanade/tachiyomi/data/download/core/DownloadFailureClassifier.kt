package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CancellationException
import rx.exceptions.OnErrorThrowable
import java.io.IOException

object DownloadFailureClassifier {

    /**
     * Maps [error] to one failure value.
     * The classifier unwraps cancellation first, then matches on exception type only.
     * It never inspects messages, so low storage stays an ERROR kind here; the
     * message-based check [isLowStorageFailure] belongs to its call sites only.
     */
    fun classify(error: Throwable): DownloadFailure {
        val unwrapped = unwrappedCancellationCause(error)
            ?: return DownloadFailure(DownloadFailureKind.CANCELLATION, error.message, error)
        val kind = when {
            unwrapped is LowStorageException -> DownloadFailureKind.LOW_STORAGE
            unwrapped is StoragePermissionException -> DownloadFailureKind.STORAGE_PERMISSION
            unwrapped is RetriesExhaustedException -> DownloadFailureKind.RETRIES_EXHAUSTED
            else -> DownloadFailureKind.GENERIC
        }
        return DownloadFailure(kind, unwrapped.message, unwrapped)
    }

    /**
     * Returns true when [e] is a filesystem failure that reports a permission problem.
     * An HTTP error body that says "Permission denied" is not a filesystem failure.
     */
    fun isPermissionFailure(e: Throwable): Boolean {
        var t: Throwable? = e
        var fsShaped = false
        while (t != null) {
            if (t is IOException || t::class.simpleName?.contains("Storage", ignoreCase = true) == true) {
                fsShaped = true
                break
            }
            t = t.cause
        }
        if (!fsShaped) return false
        t = e
        while (t != null) {
            val msg = t.message.orEmpty()
            if (msg.contains("EPERM", true) ||
                msg.contains("EACCES", true) ||
                msg.contains("Operation not permitted", true) ||
                msg.contains("Permission denied", true)
            ) {
                return true
            }
            t = t.cause
        }
        return false
    }

    /**
     * Returns true when [message] reports that the device is out of free space.
     */
    fun isLowStorageFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("No space left on device", ignoreCase = true) ||
            message.contains("ENOSPC", ignoreCase = true) ||
            message.contains("disk full", ignoreCase = true) ||
            message.contains("insufficient storage", ignoreCase = true)
    }

    /**
     * Returns the real cause when [e] only looks like coroutine cancellation,
     * or null when [e] is a genuine cancellation.
     */
    private fun unwrappedCancellationCause(e: Throwable): Throwable? {
        if (e !is CancellationException) return e
        var cause = e.cause
        while (cause != null) {
            if (cause !is CancellationException &&
                cause !is OnErrorThrowable.OnNextValue
            ) {
                return cause
            }
            cause = cause.cause
        }
        return null
    }
}
