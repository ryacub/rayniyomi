package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.cast.CastManager
import eu.kanade.tachiyomi.ui.player.cast.CastState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlaybackSpeedControllerTest {

    private val castState = MutableStateFlow(CastState.DISCONNECTED)
    private val castManager: CastManager = mockk(relaxed = true)
    private val sink: LocalSpeedSink = mockk(relaxed = true)
    private lateinit var playbackSpeed: MutableStateFlow<Float>
    private lateinit var controller: PlaybackSpeedController

    @BeforeEach
    fun setup() {
        every { castManager.castState } returns castState
        every { castManager.isCastSessionActive() } returns false
        playbackSpeed = MutableStateFlow(1.0f)
        controller = PlaybackSpeedController(castManager, playbackSpeed, sink)
    }

    private fun startCasting() {
        every { castManager.isCastSessionActive() } returns true
        castState.value = CastState.CONNECTED
    }

    // ---- Local path ----

    @Test
    fun `setSpeed writes the local renderer when no cast session is active`() {
        controller.setSpeed(1.5f)

        verify { sink.apply(1.5f) }
        verify(exactly = 0) { castManager.setPlaybackRate(any()) }
    }

    @Test
    fun `setSpeed stores the local speed as the default`() {
        controller.setSpeed(1.5f)

        verify { sink.persist(1.5f) }
    }

    @Test
    fun `setSpeed leaves playbackSpeed to the mpv observer when local`() {
        controller.setSpeed(1.5f)

        assertEquals(1.0f, playbackSpeed.value)
    }

    @Test
    fun `setSpeed does not clamp local speed`() {
        controller.setSpeed(3.0f)

        verify { sink.apply(3.0f) }
    }

    // ---- Cast path ----

    @Test
    fun `setSpeed sends the rate to the receiver while casting`() {
        startCasting()
        controller.setSpeed(1.5f)

        verify { castManager.setPlaybackRate(1.5) }
        verify(exactly = 0) { sink.apply(any()) }
    }

    @Test
    fun `setSpeed shows the requested rate immediately while casting`() {
        startCasting()
        controller.setSpeed(1.5f)

        assertEquals(1.5f, playbackSpeed.value)
    }

    // ---- Clamping ----

    @Test
    fun `setSpeed clamps a rate below the cast minimum`() {
        startCasting()
        controller.setSpeed(0.25f)

        verify { castManager.setPlaybackRate(0.5) }
        assertEquals(0.5f, playbackSpeed.value)
    }

    @Test
    fun `setSpeed clamps a rate above the cast maximum`() {
        startCasting()
        controller.setSpeed(3.0f)

        verify { castManager.setPlaybackRate(2.0) }
        assertEquals(2.0f, playbackSpeed.value)
    }

    @Test
    fun `setSpeed stores the clamped rate, not the requested one`() {
        startCasting()
        controller.setSpeed(0.25f)

        verify { sink.persist(0.5f) }
    }

    // ---- Unsupported command ----

    @Test
    fun `onReceiverStatus disables the control when the receiver lacks the rate command`() {
        controller.onReceiverStatus(rate = 1.0, isRateSupported = false)

        assertFalse(controller.isSpeedControlAvailable.value)
    }

    @Test
    fun `setSpeed is a no-op while the receiver lacks the rate command`() {
        controller.onReceiverStatus(rate = 1.0, isRateSupported = false)
        startCasting()
        controller.setSpeed(1.5f)

        verify(exactly = 0) { castManager.setPlaybackRate(any()) }
    }

    @Test
    fun `onReceiverStatus re-enables the control when support returns`() {
        controller.onReceiverStatus(1.0, false)
        controller.onReceiverStatus(1.0, true)

        assertTrue(controller.isSpeedControlAvailable.value)
    }

    // ---- Reconciliation ----

    @Test
    fun `onReceiverStatus shows the rate the receiver applied`() {
        controller.onReceiverStatus(rate = 0.5, isRateSupported = true)

        assertEquals(0.5f, playbackSpeed.value)
    }

    @Test
    fun `onReceiverStatus ignores a zero rate while the receiver buffers`() {
        playbackSpeed.value = 1.5f
        controller.onReceiverStatus(0.0, true)

        assertEquals(1.5f, playbackSpeed.value)
    }

    // ---- Session lifecycle ----

    @Test
    fun `local speed is restored when the cast session ends`() = runTest {
        controller.attach(backgroundScope)
        runCurrent()
        playbackSpeed.value = 1.25f
        startCasting()
        runCurrent()
        controller.setSpeed(2.0f)
        every { castManager.isCastSessionActive() } returns false
        castState.value = CastState.DISCONNECTED
        runCurrent()

        verify { sink.apply(1.25f) }
        assertEquals(1.25f, playbackSpeed.value)
    }

    @Test
    fun `the speed control is available again after the cast session ends`() = runTest {
        controller.attach(backgroundScope)
        runCurrent()
        controller.onReceiverStatus(1.0, false)
        startCasting()
        runCurrent()
        every { castManager.isCastSessionActive() } returns false
        castState.value = CastState.DISCONNECTED
        runCurrent()

        assertTrue(controller.isSpeedControlAvailable.value)
    }

    // ---- Boost ----

    @Test
    fun `setSpeedBoost applies locally without storing a default`() {
        controller.setSpeedBoost(2.0f)

        verify { sink.apply(2.0f) }
        verify(exactly = 0) { sink.persist(any()) }
    }
}
