package eu.kanade.tachiyomi.data.notification

import java.io.File

internal fun hasShareableErrorLogFile(errorLogFile: File?): Boolean {
    return errorLogFile?.exists() == true
}

internal fun shouldAttachRestoreErrorLogAction(errorCount: Int, errorLogFile: File?): Boolean {
    return errorCount > 0 && hasShareableErrorLogFile(errorLogFile)
}
