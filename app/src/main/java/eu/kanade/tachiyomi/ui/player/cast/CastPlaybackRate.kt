package eu.kanade.tachiyomi.ui.player.cast

/** The playback rate range the Cast SDK accepts (0.5x..2.0x). */
object CastPlaybackRate {
    // Mirrors MediaLoadOptions.PLAYBACK_RATE_MIN.
    const val MIN = 0.5f
    // Mirrors MediaLoadOptions.PLAYBACK_RATE_MAX.
    const val MAX = 2.0f

    /** Clamps [rate] to the range the Cast SDK accepts for playback rate. */
    fun clamp(rate: Float): Float = rate.coerceIn(MIN, MAX)
}