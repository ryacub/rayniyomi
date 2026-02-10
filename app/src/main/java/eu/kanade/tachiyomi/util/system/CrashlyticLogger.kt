package eu.kanade.tachiyomi.util.system

import com.google.firebase.crashlytics.FirebaseCrashlytics
import logcat.LogPriority
import logcat.logcat

/**
 * Utility for logging critical events to Firebase Crashlytics.
 * Used for crash monitoring and debugging in production.
 * Thread-safe wrapper around Firebase Crashlytics SDK.
 */
object CrashlyticLogger {

    private val crashlytics: FirebaseCrashlytics? by lazy {
        try {
            FirebaseCrashlytics.getInstance()
        } catch (_: Throwable) {
            null
        }
    }
    private val lock = Any()

    /**
     * Log a message to Crashlytics.
     * Falls back to logcat if Firebase is unavailable.
     */
    fun log(message: String) {
        logcat(LogPriority.INFO) { message }
        try {
            synchronized(lock) {
                crashlytics?.log(message)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Failed to log message to Crashlytics: $message" }
        }
    }

    /**
     * Log an exception to Crashlytics with optional context.
     * Context is preserved as a custom key for crash investigation.
     */
    fun logException(exception: Exception, context: String = "") {
        val message = if (context.isNotEmpty()) {
            "$context: ${exception.message}"
        } else {
            exception.message ?: "Unknown error"
        }
        logcat(LogPriority.ERROR) { message }
        try {
            synchronized(lock) {
                if (context.isNotEmpty()) {
                    crashlytics?.setCustomKey("exception_context", context)
                }
                crashlytics?.recordException(exception)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Failed to record exception to Crashlytics" }
        }
    }

    /**
     * Set custom string key-value pair for crash context.
     * Safe to call from any thread.
     */
    fun setCustomKey(key: String, value: String) {
        try {
            synchronized(lock) {
                crashlytics?.setCustomKey(key, value)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Failed to set custom key to Crashlytics: $key=$value" }
        }
    }

    /**
     * Set custom integer key for crash context.
     * Safe to call from any thread.
     */
    fun setCustomKey(key: String, value: Int) {
        try {
            synchronized(lock) {
                crashlytics?.setCustomKey(key, value)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Failed to set custom key to Crashlytics: $key=$value" }
        }
    }

    /**
     * Set custom boolean key for crash context.
     * Safe to call from any thread.
     */
    fun setCustomKey(key: String, value: Boolean) {
        try {
            synchronized(lock) {
                crashlytics?.setCustomKey(key, value)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "Failed to set custom key to Crashlytics: $key=$value" }
        }
    }

    /**
     * Log reader error for crash investigation
     */
    fun logReaderError(errorType: String, details: String) {
        log("Reader Error - Type: $errorType, Details: $details")
        setCustomKey("last_reader_error", errorType)
    }

    /**
     * Log player error for crash investigation
     */
    fun logPlayerError(errorType: String, details: String) {
        log("Player Error - Type: $errorType, Details: $details")
        setCustomKey("last_player_error", errorType)
    }

    /**
     * Log download error for crash investigation
     */
    fun logDownloadError(errorType: String, itemId: Long) {
        log("Download Error - Type: $errorType, ItemId: $itemId")
        setCustomKey("last_download_error", errorType)
    }
}
