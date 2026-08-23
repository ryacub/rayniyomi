package tachiyomi.core.common.util.system

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports a contained non-fatal [Throwable] to the crash service at most once per key.
 *
 * A contained fault makes no crash report on its own, so the app installs a handler during start
 * to forward reports to the crash service. This module cannot reach the crash service.
 *
 * The handler runs at most once per key for the process life. A fault in the handler must not
 * replace the original fault or kill the caller, so the handler is wrapped in a no-throw guard.
 */
object NonFatalReporter {

    private val seenKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    @Volatile
    private var handler: (Throwable, String?) -> Unit = { _, _ -> }

    fun install(handler: (Throwable, String?) -> Unit) {
        this.handler = handler
    }

    fun reportOnce(key: String, throwable: Throwable, context: String? = null) {
        // Mark the key before the handler runs. A throwing handler must not cause repeated calls.
        if (!seenKeys.add(key)) return
        try {
            handler(throwable, context)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Non-fatal report handler failed for key: $key" }
        }
    }
}
