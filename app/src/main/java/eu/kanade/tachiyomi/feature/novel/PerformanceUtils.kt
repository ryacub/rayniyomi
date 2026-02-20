package eu.kanade.tachiyomi.feature.novel

// Nearest-rank method: p95 of 100 samples is index 95
internal fun percentile(sorted: List<Long>, percentile: Int): Long {
    if (sorted.isEmpty()) return 0L
    val rank = kotlin.math.ceil(percentile / 100.0 * sorted.size).toInt()
    val index = (rank - 1).coerceIn(0, sorted.lastIndex)
    return sorted[index]
}

// Fixed-size ring buffer. Once full, overwrites oldest samples.
internal class PerformanceRingBuffer(private val capacity: Int) {
    private val samples = mutableListOf<Long>()
    private var position = 0

    @Synchronized
    fun add(value: Long) {
        if (samples.size < capacity) {
            samples.add(value)
        } else {
            samples[position] = value
            position = (position + 1) % capacity
        }
    }

    @Synchronized
    fun getSamples(): List<Long> = samples.toList()
}
