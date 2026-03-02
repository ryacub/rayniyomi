package eu.kanade.tachiyomi.data.notification

import java.io.File

sealed interface ErrorLogWriteOutcome {
    data class Created(val file: File) : ErrorLogWriteOutcome
    data object NoErrors : ErrorLogWriteOutcome
    data class Failed(val cause: Throwable? = null) : ErrorLogWriteOutcome
}

internal inline fun writeErrorLogOutcome(
    hasErrors: Boolean,
    writeFile: () -> File,
): ErrorLogWriteOutcome {
    if (!hasErrors) return ErrorLogWriteOutcome.NoErrors
    return runCatching { writeFile() }
        .fold(
            onSuccess = { ErrorLogWriteOutcome.Created(it) },
            onFailure = { ErrorLogWriteOutcome.Failed(it) },
        )
}

internal fun hasShareableErrorLogFile(errorLogFile: File): Boolean {
    return errorLogFile.exists()
}

internal fun resolveRestoreErrorLogFileForAction(
    errorCount: Int,
    errorLogWriteOutcome: ErrorLogWriteOutcome,
): File? {
    if (errorCount <= 0) return null
    val errorLogFile = (errorLogWriteOutcome as? ErrorLogWriteOutcome.Created)?.file ?: return null
    return errorLogFile.takeIf(::hasShareableErrorLogFile)
}

internal fun shouldAttachRestoreErrorLogAction(
    errorCount: Int,
    errorLogWriteOutcome: ErrorLogWriteOutcome,
): Boolean {
    return resolveRestoreErrorLogFileForAction(errorCount, errorLogWriteOutcome) != null
}
