package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastSession
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CastManagerTest {

    private lateinit var castManager: CastManager
    private val mockContext: Context = mockk(relaxed = true)
    private val mockNetwork: NetworkHelper = mockk(relaxed = true)
    private val mockPlayerPreferences: PlayerPreferences = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        castManager = CastManager(mockContext, mockNetwork, mockPlayerPreferences)
    }

    @Test
    fun `castState initial value is DISCONNECTED`() = runTest {
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `castState transitions to CONNECTING when a session starts`() = runTest {
        castManager.onSessionStarting()
        assertEquals(CastState.CONNECTING, castManager.castState.first())
    }

    @Test
    fun `castState transitions to CONNECTING when a session resumes`() = runTest {
        castManager.onSessionResuming()
        assertEquals(CastState.CONNECTING, castManager.castState.first())
    }

    @Test
    fun `castState transitions to CONNECTED when onSessionConnected is called`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        val state = castManager.castState.first()
        assertEquals(CastState.CONNECTED, state)
    }

    @Test
    fun `castState transitions back to DISCONNECTED when onSessionEnded is called`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.onSessionEnded()
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `castState transitions to DISCONNECTED on session resume failure`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.onSessionResumeFailed()
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `castState transitions to DISCONNECTED on session start failure`() = runTest {
        castManager.onSessionStarting()
        castManager.onSessionStartFailed()
        assertEquals(CastState.DISCONNECTED, castManager.castState.first())
    }

    @Test
    fun `castState transitions to DISCONNECTED when a session ends`() = runTest {
        castManager.onSessionConnected(mockk(relaxed = true))
        castManager.onSessionEnding()
        assertEquals(CastState.DISCONNECTED, castManager.castState.first())
    }

    @Test
    fun `resetForNewActivity clears stale session reference`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.resetForNewActivity()
        // After reset, state should be DISCONNECTED
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `setPlaybackRate forwards the rate to the remote media client`() {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)

        castManager.setPlaybackRate(1.5)

        verify { mockSession.remoteMediaClient?.setPlaybackRate(1.5) }
    }

    @Test
    fun `isPlaybackRateSupported is false without a session`() {
        assertEquals(false, castManager.isPlaybackRateSupported())
    }

    @Test
    fun `isDownloadedVideo is true for a content URI`() {
        val video = Video(videoUrl = "content://downloads/episode.mp4")
        assertEquals(true, castManager.isDownloadedVideo(video))
    }

    @Test
    fun `isDownloadedVideo is false for a streaming URL`() {
        val video = Video(videoUrl = "https://example.com/video.mp4")
        assertEquals(false, castManager.isDownloadedVideo(video))
    }
}
