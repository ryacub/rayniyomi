package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastSession
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CastManagerTest {

    private lateinit var castManager: CastManager
    private val mockContext: Context = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        castManager = CastManager(mockContext)
    }

    @Test
    fun `castState initial value is DISCONNECTED`() = runTest {
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
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
    fun `resetForNewActivity clears stale session reference`() = runTest {
        val mockSession: CastSession = mockk(relaxed = true)
        castManager.onSessionConnected(mockSession)
        castManager.resetForNewActivity()
        // After reset, state should be DISCONNECTED
        val state = castManager.castState.first()
        assertEquals(CastState.DISCONNECTED, state)
    }

    @Test
    fun `cleanup unregisters session listener`() {
        // Should not throw; verifies listener cleanup runs without error
        castManager.cleanup()
    }
}
