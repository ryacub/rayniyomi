package eu.kanade.presentation.theme.cover

import android.content.Context
import android.graphics.Bitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object CoverThemePaletteService {
    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CoverThemeTokens?>()
    private const val MAX_CACHE_SIZE = 128

    suspend fun tokensFor(
        context: Context,
        data: Any,
        cacheKey: String,
        isDark: Boolean,
    ): CoverThemeTokens? {
        val scopedKey = "$cacheKey;$isDark"
        cacheMutex.withLock {
            if (cache.containsKey(scopedKey)) {
                return cache[scopedKey]
            }
        }

        val bitmap = loadBitmap(context, data) ?: return putAndReturn(scopedKey, null)
        val seed = extractDominantColor(bitmap) ?: return putAndReturn(scopedKey, null)
        return putAndReturn(scopedKey, CoverThemeColorUtils.buildTokens(seed, isDark))
    }

    private suspend fun putAndReturn(key: String, value: CoverThemeTokens?): CoverThemeTokens? {
        cacheMutex.withLock {
            cache[key] = value
            while (cache.size > MAX_CACHE_SIZE) {
                val first = cache.entries.firstOrNull()?.key ?: break
                cache.remove(first)
            }
        }
        return value
    }

    private suspend fun loadBitmap(context: Context, data: Any): Bitmap? {
        return withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(data)
                .size(Size(128, 128))
                .build()
            when (val result = context.imageLoader.execute(request)) {
                is SuccessResult -> {
                    result.image.asDrawable(context.resources).getBitmapOrNull()
                }
                is ErrorResult -> {
                    logcat(LogPriority.WARN, result.throwable) { "Failed to decode cover for dynamic theme" }
                    null
                }
            }
        }
    }

    private fun extractDominantColor(bitmap: Bitmap): androidx.compose.ui.graphics.Color? {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L

        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha >= 128) {
                    sumR += android.graphics.Color.red(pixel)
                    sumG += android.graphics.Color.green(pixel)
                    sumB += android.graphics.Color.blue(pixel)
                    count++
                }
                y += stepY
            }
            x += stepX
        }

        if (count == 0L) return null

        val r = (sumR / count).toInt().coerceIn(0, 255)
        val g = (sumG / count).toInt().coerceIn(0, 255)
        val b = (sumB / count).toInt().coerceIn(0, 255)
        return androidx.compose.ui.graphics.Color(android.graphics.Color.rgb(r, g, b))
    }
}
