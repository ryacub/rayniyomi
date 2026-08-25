package eu.kanade.tachiyomi.data.download.manga

import java.io.IOException
import kotlinx.coroutines.CancellationException
import rx.exceptions.OnErrorThrowable

internal class StoragePermissionException(message: String?, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Carries the real last failure when all image download retries are spent.
 */
internal class RetriesExhaustedException(cause: Throwable) : Exception(
    buildString {
        append(cause::class.simpleName ?: "Unknown")
        cause.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
    },
    cause,
)

/**
 * Returns true when [e] is a filesystem failure that reports a permission problem.
 * An HTTP error body that says "Permission denied" is not a filesystem failure.
 */
internal fun isPermissionFailure(e: Throwable): Boolean {
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
 * Returns the real cause when [e] only looks like coroutine cancellation,
 * or null when [e] is a genuine cancellation.
 */
internal fun unwrappedCancellationCause(e: Throwable): Throwable? {
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
