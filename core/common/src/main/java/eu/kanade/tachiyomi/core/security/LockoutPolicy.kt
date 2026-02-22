package eu.kanade.tachiyomi.core.security

/**
 * Represents the lockout state after a failed PIN attempt.
 */
sealed interface LockoutState {
    /**
     * User is allowed to attempt PIN entry.
     */
    data object Allowed : LockoutState

    /**
     * User is locked out for a specific duration.
     *
     * @property durationMillis The lockout duration in milliseconds.
     */
    data class LockedOut(val durationMillis: Long) : LockoutState

    /**
     * User has exceeded maximum attempts and the app should close.
     */
    data object CloseApp : LockoutState
}

/**
 * Defines escalating lockout policy for failed PIN attempts.
 *
 * Lockout rules:
 * - Attempts 1-3: Allowed
 * - Attempts 4-6: 30-second lockout
 * - Attempts 7-9: 5-minute lockout
 * - Attempts 10+: Close app
 */
object LockoutPolicy {
    const val LOCKOUT_30_SECONDS = 30_000L
    const val LOCKOUT_5_MINUTES = 300_000L

    /**
     * Calculate the lockout state for a given failed attempt count.
     *
     * @param failedAttempts The number of consecutive failed PIN attempts.
     * @return The lockout state for this attempt count.
     */
    fun calculateLockout(failedAttempts: Int): LockoutState {
        return when {
            failedAttempts <= 3 -> LockoutState.Allowed
            failedAttempts in 4..6 -> LockoutState.LockedOut(LOCKOUT_30_SECONDS)
            failedAttempts in 7..9 -> LockoutState.LockedOut(LOCKOUT_5_MINUTES)
            else -> LockoutState.CloseApp
        }
    }

    /**
     * Calculate remaining lockout time in seconds.
     *
     * @param lockoutUntil The timestamp (millis) when lockout expires.
     * @return Remaining seconds, or 0 if lockout has expired.
     */
    fun calculateRemainingSeconds(lockoutUntil: Long): Int {
        val now = System.currentTimeMillis()
        val remaining = lockoutUntil - now
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }

    /**
     * Check if user is currently locked out.
     *
     * @param lockoutUntil The timestamp (millis) when lockout expires, or 0 if not locked out.
     * @return True if lockout is active, false otherwise.
     */
    fun isLockedOut(lockoutUntil: Long): Boolean {
        if (lockoutUntil == 0L) return false
        return System.currentTimeMillis() < lockoutUntil
    }
}
