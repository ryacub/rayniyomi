package eu.kanade.tachiyomi.data.download.core

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
 * Signals that the device does not have enough free space to continue the download.
 */
internal class LowStorageException(message: String) : Exception(message)
