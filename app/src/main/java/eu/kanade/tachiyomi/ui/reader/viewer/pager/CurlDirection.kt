package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Direction the page curls from. The math works in a canonical frame that
 * curls from the right edge; [FROM_LEFT] mirrors it about the center line.
 */
enum class CurlDirection {
    FROM_RIGHT,
    FROM_LEFT,
    ;

    /** Mirrors [x] about the page center when this direction is [FROM_LEFT]. */
    fun mirrorX(x: Float, pageWidth: Float): Float =
        if (this == FROM_RIGHT) x else pageWidth - x

    /**
     * Mirrors [span] about the page center when this direction is
     * [FROM_LEFT]. The mirror swaps the two endpoints. The result is
     * sorted, so `start` is never greater than `endInclusive`.
     */
    fun mirrorSpan(
        span: ClosedFloatingPointRange<Float>,
        pageWidth: Float,
    ): ClosedFloatingPointRange<Float> =
        if (this == FROM_RIGHT) {
            span
        } else {
            mirrorX(span.endInclusive, pageWidth)..mirrorX(span.start, pageWidth)
        }
}
