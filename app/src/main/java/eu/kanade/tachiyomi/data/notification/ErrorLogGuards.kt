package eu.kanade.tachiyomi.data.notification

import java.io.File

sealed interface ErrorLogFileResult {
    data class Created(val file: File) : ErrorLogFileResult

    data object NoErrors : ErrorLogFileResult

    data class Failed(val cause: Throwable) : ErrorLogFileResult
}

internal fun asShareableErrorLogFile(errorLogFileResult: ErrorLogFileResult): File? {
    return when (errorLogFileResult) {
        is ErrorLogFileResult.Created -> errorLogFileResult.file.takeIf { it.exists() }
        ErrorLogFileResult.NoErrors -> null
        is ErrorLogFileResult.Failed -> null
    }
}

internal fun shouldAttachRestoreErrorLogAction(errorCount: Int, errorLogFileResult: ErrorLogFileResult): Boolean {
    return errorCount > 0 && asShareableErrorLogFile(errorLogFileResult) != null
}
