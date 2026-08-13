package tachiyomi.core.common.util.lang

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Reports a contained [SourceLinkageException].
 *
 * A contained fault makes no crash report, so nothing reaches the crash service on its own. The
 * extension R8 mapping is also not available to this app, so an obfuscated frame can never name the
 * extension. The report at the point of failure is therefore the only record that identifies it.
 *
 * This module cannot reach the crash service. The app sets [onFailure] during start.
 */
object SourceLinkageReporter {

    @Volatile
    var onFailure: (SourceLinkageException) -> Unit = {}

    fun report(failure: SourceLinkageException) {
        logcat(LogPriority.ERROR, failure) { failure.message ?: "Broken extension" }
        // The app supplies this. A fault in the reporter must not replace the original fault.
        runCatching { onFailure(failure) }
    }
}
