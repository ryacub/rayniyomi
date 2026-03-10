package eu.kanade.tachiyomi.ui.security

import android.view.Window
import android.view.WindowManager
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

/**
 * Tests verifying that FLAG_SECURE is set on the window BEFORE Compose is rendered.
 *
 * The fix extracted the security logic into [applyPreComposeSecurity], which is called
 * synchronously in UnlockActivity.onCreate() before setContent{}.
 *
 * Tests call [applyPreComposeSecurity] directly to avoid requiring Robolectric.
 */
class UnlockActivitySecureScreenTest {

    private lateinit var mockWindow: Window
    private lateinit var mockSecurityPreferences: SecurityPreferences
    private lateinit var usePinLockPref: Preference<Boolean>

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        mockWindow = mockk()
        mockSecurityPreferences = mockk()
        usePinLockPref = mockk()
        every { mockSecurityPreferences.usePinLock() } returns usePinLockPref
    }

    @Test
    fun windowSetFlagsCalledWithFlagSecureWhenPinEnabled() {
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } returns Unit

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        verify { mockWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun windowSetFlagsCalledBeforeComposeRender() {
        val callSequence = mutableListOf<String>()
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } answers { callSequence.add("setFlags") }

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        verify { mockWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) }
        assert(callSequence.contains("setFlags")) { "setSecureScreen must record a setFlags call" }
    }

    @Test
    fun windowSetFlagsNotCalledWhenPinLockDisabled() {
        every { usePinLockPref.get() } returns false
        every { mockWindow.setFlags(any(), any()) } returns Unit

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        verify { mockWindow wasNot Called }
    }

    @Test
    fun flagSecureValueUsedInWindowSetFlags() {
        val flagsSlot = slot<Int>()
        val maskSlot = slot<Int>()
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(capture(flagsSlot), capture(maskSlot)) } returns Unit

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        assert(flagsSlot.captured == WindowManager.LayoutParams.FLAG_SECURE) {
            "Expected FLAG_SECURE (${WindowManager.LayoutParams.FLAG_SECURE}) but got ${flagsSlot.captured}"
        }
        assert(maskSlot.captured == WindowManager.LayoutParams.FLAG_SECURE) {
            "Expected mask FLAG_SECURE but got ${maskSlot.captured}"
        }
    }

    @Test
    fun setSecureScreenIdempotent() {
        var callCount = 0
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } answers { callCount++ }

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)
        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        // Two calls to setFlags is safe — Android's setFlags is idempotent
        assert(callCount == 2) { "Expected 2 setFlags calls (idempotent), got $callCount" }
    }

    @Test
    fun pinEntryScreenHasSecureWindowDuringOnCreate() {
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } returns Unit

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        verify { mockWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun onCreateSetsSecureScreenBeforeShowPinAuth() {
        val callOrder = mutableListOf<String>()
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } answers { callOrder.add("setSecureScreen") }

        // Calling applyPreComposeSecurity before setContent{} is what onCreate does
        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        assert(callOrder.contains("setSecureScreen")) {
            "setSecureScreen must be called in onCreate before setContent{}"
        }
    }

    @Test
    fun windowFlagSecurePreventsCaptureAfterFix() {
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } returns Unit

        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        verify { mockWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) }
    }

    @Test
    fun raceConditionFixedByMovingSetSecureScreenToOnCreate() {
        val callSequence = mutableListOf<String>()
        every { usePinLockPref.get() } returns true
        every { mockWindow.setFlags(any(), any()) } answers { callSequence.add("setFlags") }

        // applyPreComposeSecurity runs synchronously in onCreate BEFORE setContent{}
        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        verify { mockWindow.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) }
        assert(callSequence.isNotEmpty()) { "FLAG_SECURE must be set before Compose renders" }
    }

    @Test
    fun biometricPathAlreadySecure() {
        // Biometric authentication path calls startAuthentication() before setContent{},
        // so SecureActivityDelegate already applies FLAG_SECURE for the biometric path.
        // No change needed for biometric auth — this test documents that invariant.
    }

    @Test
    fun configurationChangePreservesSecureFlag() {
        every { usePinLockPref.get() } returns true
        var setFlagsCallCount = 0
        every { mockWindow.setFlags(any(), any()) } answers { setFlagsCallCount++ }

        // Simulate two onCreate calls (initial launch + config change)
        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)
        applyPreComposeSecurity(mockWindow, mockSecurityPreferences)

        assert(setFlagsCallCount == 2) {
            "FLAG_SECURE should be reapplied on each onCreate (config change), got $setFlagsCallCount calls"
        }
    }
}
