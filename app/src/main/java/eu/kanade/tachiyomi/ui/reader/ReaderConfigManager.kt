package eu.kanade.tachiyomi.ui.reader

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.core.net.toUri
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.hippo.unifile.UniFile
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat

/**
 * Manages reactive configuration subscriptions for the reader.
 * Computes derived values (brightness, colors, display profile) without Android Window/View references.
 * Extracted from ReaderActivity to improve separation of concerns and testability.
 *
 * All StateFlows emit initial values from preferences immediately, then react to preference changes.
 * All file I/O operations are executed on IO dispatcher to prevent blocking the main thread.
 */
class ReaderConfigManager(
    private val readerPreferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
    private val scope: CoroutineScope,
    private val isNightMode: Boolean,
) {
    private val _backgroundColor = MutableStateFlow(Color.BLACK)
    val backgroundColor: StateFlow<Int> = _backgroundColor.asStateFlow()

    private val _layerPaint = MutableStateFlow<Paint?>(null)
    val layerPaint: StateFlow<Paint?> = _layerPaint.asStateFlow()

    private val _displayProfile = MutableStateFlow<ByteArray?>(null)
    val displayProfile: StateFlow<ByteArray?> = _displayProfile.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _cutoutShort = MutableStateFlow(false)
    val cutoutShort: StateFlow<Boolean> = _cutoutShort.asStateFlow()

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen: StateFlow<Boolean> = _fullscreen.asStateFlow()

    private val _customBrightnessEnabled = MutableStateFlow(false)
    val customBrightnessEnabled: StateFlow<Boolean> = _customBrightnessEnabled.asStateFlow()

    private val _customBrightnessValue = MutableStateFlow(0)
    val customBrightnessValue: StateFlow<Int> = _customBrightnessValue.asStateFlow()

    init {
        observeReaderTheme()
        observeDisplayProfile()
        observeCutoutShort()
        observeKeepScreenOn()
        observeCustomBrightness()
        observeColorFilters()
        observeFullscreen()
    }

    companion object {
        // Reader theme constants
        private const val THEME_WHITE = 0
        private const val THEME_GRAY = 2
        private const val THEME_AUTOMATIC = 3

        // Brightness calculation constant
        private const val BRIGHTNESS_OVERRIDE_NONE = -1.0f

        /**
         * Calculate reader brightness value for window attributes.
         * Range is [-75, 100].
         * From -75 to -1: minimum brightness (0.01f)
         * From 1 to 100: percentage brightness
         * 0: system brightness (BRIGHTNESS_OVERRIDE_NONE)
         */
        fun calculateBrightness(value: Int): Float {
            return when {
                value > 0 -> value / 100f
                value < 0 -> 0.01f
                else -> BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }

    private fun observeReaderTheme() {
        readerPreferences.readerTheme().changes()
            .onStart { emit(readerPreferences.readerTheme().get()) }
            .onEach { theme ->
                _backgroundColor.update {
                    when (theme) {
                        THEME_WHITE -> Color.WHITE
                        THEME_GRAY -> getGrayBackgroundColor()
                        THEME_AUTOMATIC -> automaticBackgroundColor()
                        else -> Color.BLACK // THEME_BLACK or unknown
                    }
                }
            }
            .launchIn(scope)
    }

    private fun getGrayBackgroundColor(): Int {
        return Color.rgb(0x20, 0x21, 0x25)
    }

    private fun observeDisplayProfile() {
        basePreferences.displayProfile().changes()
            .onStart { emit(basePreferences.displayProfile().get()) }
            .onEach { path ->
                val profile = withContext(Dispatchers.IO) {
                    loadDisplayProfile(path)
                }
                _displayProfile.update { profile }
                profile?.let {
                    SubsamplingScaleImageView.setDisplayProfile(it)
                    TachiyomiImageDecoder.displayProfile = it
                }
            }
            .launchIn(scope)
    }

    private fun observeCutoutShort() {
        readerPreferences.cutoutShort().changes()
            .onStart { emit(readerPreferences.cutoutShort().get()) }
            .onEach { enabled ->
                _cutoutShort.update { enabled }
            }
            .launchIn(scope)
    }

    private fun observeKeepScreenOn() {
        readerPreferences.keepScreenOn().changes()
            .onStart { emit(readerPreferences.keepScreenOn().get()) }
            .onEach { enabled ->
                _keepScreenOn.update { enabled }
            }
            .launchIn(scope)
    }

    private fun observeCustomBrightness() {
        readerPreferences.customBrightness().changes()
            .onStart { emit(readerPreferences.customBrightness().get()) }
            .onEach { enabled ->
                _customBrightnessEnabled.update { enabled }
                if (!enabled) {
                    _customBrightnessValue.update { 0 }
                }
            }
            .launchIn(scope)

        readerPreferences.customBrightnessValue().changes()
            .onStart { emit(readerPreferences.customBrightnessValue().get()) }
            .onEach { value ->
                if (_customBrightnessEnabled.value) {
                    _customBrightnessValue.update { value }
                }
            }
            .launchIn(scope)
    }

    private fun observeColorFilters() {
        merge(
            readerPreferences.grayscale().changes().onStart { emit(readerPreferences.grayscale().get()) },
            readerPreferences.invertedColors().changes().onStart { emit(readerPreferences.invertedColors().get()) },
        )
            .onEach {
                val grayscale = readerPreferences.grayscale().get()
                val invertedColors = readerPreferences.invertedColors().get()
                _layerPaint.update {
                    if (grayscale || invertedColors) {
                        getCombinedPaint(grayscale, invertedColors)
                    } else {
                        null
                    }
                }
            }
            .launchIn(scope)
    }

    private fun observeFullscreen() {
        readerPreferences.fullscreen().changes()
            .onStart { emit(readerPreferences.fullscreen().get()) }
            .onEach { enabled ->
                _fullscreen.update { enabled }
            }
            .launchIn(scope)
    }

    private fun automaticBackgroundColor(): Int {
        return if (isNightMode) {
            getGrayBackgroundColor()
        } else {
            Color.WHITE
        }
    }

    private fun loadDisplayProfile(path: String): ByteArray? {
        if (path.isBlank()) return null

        val file = UniFile.fromUri(null, path.toUri()) ?: return null
        if (!file.exists()) return null

        return try {
            file.openInputStream().use { it.readBytes() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to load display profile from $path: ${e.message}" }
            null
        }
    }

    private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
        return Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    if (grayscale) {
                        setSaturation(0f)
                    }
                    if (invertedColors) {
                        postConcat(
                            ColorMatrix(
                                floatArrayOf(
                                    -1f, 0f, 0f, 0f, 255f,
                                    0f, -1f, 0f, 0f, 255f,
                                    0f, 0f, -1f, 0f, 255f,
                                    0f, 0f, 0f, 1f, 0f,
                                ),
                            ),
                        )
                    }
                },
            )
        }
    }

    /**
     * Calculate reader brightness value for window attributes.
     * Delegates to companion object for testability.
     */
    fun calculateReaderBrightness(value: Int): Float = calculateBrightness(value)
}
