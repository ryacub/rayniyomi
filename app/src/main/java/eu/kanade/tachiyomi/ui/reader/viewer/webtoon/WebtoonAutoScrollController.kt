package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WebtoonAutoScrollController(
    private val scope: CoroutineScope,
    private val canScrollDown: () -> Boolean,
    private val onScrollBy: (Int) -> Unit,
    private val onStateChanged: (Boolean) -> Unit,
    private val onReachedEnd: () -> Unit,
    private val basePxPerSecond: Float = BASE_PX_PER_SECOND,
    private val tickDelayMs: Long = TICK_DELAY_MS,
) {

    private var speedTenths = DEFAULT_SPEED_TENTHS
    private var running = false
    private var remainderPixels = 0f
    private var scrollJob: Job? = null

    fun start() {
        if (running) return
        if (!canScrollDown()) {
            onReachedEnd()
            return
        }

        running = true
        onStateChanged(true)

        scrollJob = scope.launch {
            while (isActive && running) {
                delay(tickDelayMs)

                if (!canScrollDown()) {
                    pauseInternal()
                    onReachedEnd()
                    break
                }

                val delta = pixelsPerTick() + remainderPixels
                val deltaInt = delta.toInt()
                remainderPixels = delta - deltaInt

                if (deltaInt > 0) {
                    onScrollBy(deltaInt)
                }
            }
        }
    }

    fun pause() {
        if (!running) return
        pauseInternal()
    }

    fun toggle() {
        if (running) {
            pause()
        } else {
            start()
        }
    }

    fun stop() {
        pause()
    }

    fun setSpeedTenths(value: Int) {
        speedTenths = value.coerceIn(
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN,
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX,
        )
    }

    fun isRunning(): Boolean {
        return running
    }

    private fun pauseInternal() {
        running = false
        remainderPixels = 0f
        scrollJob?.cancel()
        scrollJob = null
        onStateChanged(false)
    }

    private fun pixelsPerTick(): Float {
        val speedMultiplier = speedTenths / SPEED_DIVISOR
        return basePxPerSecond * speedMultiplier * (tickDelayMs / MILLIS_PER_SECOND)
    }

    private companion object {
        private const val BASE_PX_PER_SECOND = 150f
        private const val TICK_DELAY_MS = 16L
        private const val DEFAULT_SPEED_TENTHS = 10
        private const val SPEED_DIVISOR = 10f
        private const val MILLIS_PER_SECOND = 1000f
    }
}
