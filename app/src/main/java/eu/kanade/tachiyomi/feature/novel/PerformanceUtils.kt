package eu.kanade.tachiyomi.feature.novel

/**
 * Computes the percentile value from a sorted list of samples.
 *
 * Uses nearest-rank method (ceiling): p95 of 100 samples is index 95.
 *
 * @param sorted Sorted list of samples (ascending order).
 * @param percentile Percentile to compute (0-100).
 * @return The percentile value.
 */
internal fun percentile(sorted: List<Long>, percentile: Int): Long {
    if (sorted.isEmpty()) return 0L
    // Nearest-rank method: ceiling of (percentile/100 * n)
    val rank = kotlin.math.ceil(percentile / 100.0 * sorted.size).toInt()
    val index = (rank - 1).coerceIn(0, sorted.lastIndex)
    return sorted[index]
}

/**
 * Ring buffer for storing fixed-size sample history.
 *
 * Maintains a circular buffer of long values with a fixed capacity.
 * Once full, new samples overwrite the oldest samples.
 *
 * Thread-safe: all public methods are synchronized.
 *
 * @property capacity Maximum number of samples to store.
 */
internal class PerformanceRingBuffer(private val capacity: Int) {
    private val samples = mutableListOf<Long>()
    private var position = 0

    /**
     * Adds a sample to the ring buffer.
     *
     * If the buffer is not full, the sample is appended.
     * If the buffer is full, the sample overwrites the oldest entry.
     *
     * @param value The sample value to add.
     */
    @Synchronized
    fun add(value: Long) {
        if (samples.size < capacity) {
            samples.add(value)
        } else {
            samples[position] = value
            position = (position + 1) % capacity
        }
    }

    /**
     * Returns a defensive copy of the current samples.
     *
     * The returned list is a snapshot and will not be modified by
     * subsequent add() calls.
     *
     * @return List of samples in insertion order.
     */
    @Synchronized
    fun getSamples(): List<Long> = samples.toList()
}
