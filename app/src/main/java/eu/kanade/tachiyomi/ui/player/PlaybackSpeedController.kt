package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.cast.CastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Applies a local speed change. Kept behind an interface so tests never load the mpv JNI class. */
interface LocalSpeedSink {
    /** Sends the speed to the local renderer. */
    fun apply(speed: Float)

    /** Stores the speed as the default for the next local playback. */
    fun persist(speed: Float)
}

/**
 * Single boundary for every runtime playback speed change (R1033).
 *
 * Local playback writes the mpv property. An active Cast session writes the
 * receiver rate instead, clamped to the range the Cast SDK accepts.
 */
class PlaybackSpeedController(
    private val castManager: CastManager,
    private val playbackSpeed: MutableStateFlow<Float>,
    private val localSink: LocalSpeedSink,
) {

    private val _isSpeedControlAvailable = MutableStateFlow(true)
    val isSpeedControlAvailable: StateFlow<Boolean> = _isSpeedControlAvailable.asStateFlow()

    private var localSpeedBeforeCast: Float? = null

    /** Starts watching the Cast session so local speed can be restored when it ends. */
    fun attach(scope: CoroutineScope) {
        castManager.castState
            .onEach { state -> onCastStateChanged(state) }
            .launchIn(scope)
    }

    /** Applies a user speed change to whichever renderer is playing. */
    fun setSpeed(requested: Float) {
        TODO("R1033")
    }

    /** Applies a temporary hold-to-boost speed. Never stored as the default. */
    fun setSpeedBoost(speed: Float) {
        TODO("R1033")
    }

    /** Reconciles the shown speed with what the receiver reports. */
    fun onReceiverStatus(rate: Double, isRateSupported: Boolean) {
        TODO("R1033")
    }

    private fun setLocalSpeed(speed: Float) {
        TODO("R1033")
    }

    private fun setCastSpeed(requested: Float) {
        TODO("R1033")
    }

    private fun onCastStateChanged(state: CastState) {
        TODO("R1033")
    }

    private fun restoreLocalSpeed() {
        TODO("R1033")
    }
}