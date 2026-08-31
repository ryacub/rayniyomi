package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.cast.CastPlaybackRate
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
        if (!castManager.isCastSessionActive()) {
            setLocalSpeed(requested)
            return
        }
        if (!_isSpeedControlAvailable.value) return
        setCastSpeed(requested)
    }

    /** Applies a temporary hold-to-boost speed. Never stored as the default. */
    fun setSpeedBoost(speed: Float) {
        localSink.apply(speed)
    }

    /** Reconciles the shown speed with what the receiver reports. */
    fun onReceiverStatus(rate: Double, isRateSupported: Boolean) {
        _isSpeedControlAvailable.value = isRateSupported
        if (rate <= 0.0) return // The receiver reports 0 while it buffers.
        playbackSpeed.value = rate.toFloat()
    }

    private fun setLocalSpeed(speed: Float) {
        localSink.apply(speed)
        localSink.persist(speed)
        // playbackSpeed is not written here: the mpv "speed" property observer owns it.
    }

    private fun setCastSpeed(requested: Float) {
        val effective = CastPlaybackRate.clamp(requested)
        castManager.setPlaybackRate(effective.toDouble())
        playbackSpeed.value = effective
        localSink.persist(effective)
    }

    private fun onCastStateChanged(state: CastState) {
        if (state == CastState.CONNECTED) {
            if (localSpeedBeforeCast == null) localSpeedBeforeCast = playbackSpeed.value
            return
        }
        restoreLocalSpeed()
    }

    private fun restoreLocalSpeed() {
        _isSpeedControlAvailable.value = true
        val speed = localSpeedBeforeCast ?: return
        localSpeedBeforeCast = null
        playbackSpeed.value = speed
        localSink.apply(speed)
    }
}