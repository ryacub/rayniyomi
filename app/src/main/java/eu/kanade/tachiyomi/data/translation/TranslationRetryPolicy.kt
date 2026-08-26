package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Decides which translation failures are worth retrying and how long to wait between attempts.
 *
 * Extracted from TranslationManager so the classification and the backoff schedule can be tested
 * without a page loop, an engine, or storage.
 */
class TranslationRetryPolicy(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
) {

    /**
     * True only for HTTP status codes where a retry can succeed: rate limiting (429) and server
     * errors (5xx). All other failures are permanent for this page.
     */
    fun isTransient(error: Throwable): Boolean {
        val httpException = error as? HttpException ?: return false
        return httpException.code == 429 || httpException.code in 500..599
    }

    /** Backoff before the retry that follows attempt [attempt] (0-based). */
    fun delayForAttempt(attempt: Int): Long = baseDelayMs shl attempt

    /**
     * Runs [block], retrying transient failures with exponential backoff.
     * [label] names the work in the retry log, for example chapter and page.
     * [onRetry] runs after each failed attempt, before its backoff delay, with the 0-based index of
     * the attempt that just failed.
     * Rethrows cancellation, permanent failures, and the last error after [maxRetries] retries.
     */
    suspend fun <T> execute(
        label: String,
        onRetry: (attempt: Int) -> Unit = {},
        block: suspend () -> T,
    ): T {
        // maxRetries counts retries after the first attempt; total calls = maxRetries + 1.
        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == maxRetries || !isTransient(e)) throw e
                logcat(LogPriority.WARN) {
                    "Transient translation failure for $label (attempt ${attempt + 1}), retrying"
                }
                onRetry(attempt)
                delay(delayForAttempt(attempt))
            }
        }
        throw IllegalStateException("unreachable")
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
        const val DEFAULT_BASE_DELAY_MS = 2_000L
    }
}
