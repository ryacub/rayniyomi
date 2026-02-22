package eu.kanade.tachiyomi.ui.security

import eu.kanade.tachiyomi.core.security.AuthMethod
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class AuthenticationOrchestratorTest {

    private val prefs = mockk<SecurityPreferences>()

    // resolvePrimaryMethod tests (5 tests)

    @Test
    fun `resolvePrimaryMethod returns Biometric when only biometric enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns true
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns false

        val result = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)

        assertEquals(AuthMethod.Biometric, result)
    }

    @Test
    fun `resolvePrimaryMethod returns PIN when only PIN enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns false
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns true

        val result = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)

        assertEquals(AuthMethod.Pin, result)
    }

    @Test
    fun `resolvePrimaryMethod returns None when neither enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns false
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns false

        val result = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)

        assertEquals(AuthMethod.None, result)
    }

    @Test
    fun `resolvePrimaryMethod returns Biometric when both enabled and biometric is primary`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        val primaryAuthMethodPref = mockk<Preference<SecurityPreferences.PrimaryAuthMethod>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns true
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns true
        every { prefs.primaryAuthMethod() } returns primaryAuthMethodPref
        every { primaryAuthMethodPref.get() } returns SecurityPreferences.PrimaryAuthMethod.BIOMETRIC

        val result = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)

        assertEquals(AuthMethod.Biometric, result)
    }

    @Test
    fun `resolvePrimaryMethod returns PIN when both enabled and PIN is primary`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        val primaryAuthMethodPref = mockk<Preference<SecurityPreferences.PrimaryAuthMethod>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns true
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns true
        every { prefs.primaryAuthMethod() } returns primaryAuthMethodPref
        every { primaryAuthMethodPref.get() } returns SecurityPreferences.PrimaryAuthMethod.PIN

        val result = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)

        assertEquals(AuthMethod.Pin, result)
    }

    // resolveFallbackMethod tests (4 tests)

    @Test
    fun `resolveFallbackMethod returns PIN when primary is Biometric and PIN enabled`() {
        val usePinLockPref = mockk<Preference<Boolean>>()
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns true

        val result = AuthenticationOrchestrator.resolveFallbackMethod(AuthMethod.Biometric, prefs)

        assertEquals(AuthMethod.Pin, result)
    }

    @Test
    fun `resolveFallbackMethod returns Biometric when primary is PIN and Biometric enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns true

        val result = AuthenticationOrchestrator.resolveFallbackMethod(AuthMethod.Pin, prefs)

        assertEquals(AuthMethod.Biometric, result)
    }

    @Test
    fun `resolveFallbackMethod returns None when primary is Biometric and PIN not enabled`() {
        val usePinLockPref = mockk<Preference<Boolean>>()
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns false

        val result = AuthenticationOrchestrator.resolveFallbackMethod(AuthMethod.Biometric, prefs)

        assertEquals(AuthMethod.None, result)
    }

    @Test
    fun `resolveFallbackMethod returns None when primary is PIN and Biometric not enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns false

        val result = AuthenticationOrchestrator.resolveFallbackMethod(AuthMethod.Pin, prefs)

        assertEquals(AuthMethod.None, result)
    }

    // hasFallbackAvailable tests (2 tests)

    @Test
    fun `hasFallbackAvailable returns true when both auth methods enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        val primaryAuthMethodPref = mockk<Preference<SecurityPreferences.PrimaryAuthMethod>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns true
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns true
        every { prefs.primaryAuthMethod() } returns primaryAuthMethodPref
        every { primaryAuthMethodPref.get() } returns SecurityPreferences.PrimaryAuthMethod.BIOMETRIC

        val primaryMethod = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)
        val result = AuthenticationOrchestrator.hasFallbackAvailable(primaryMethod, prefs)

        assertTrue(result)
    }

    @Test
    fun `hasFallbackAvailable returns false when only one auth method enabled`() {
        val useAuthenticatorPref = mockk<Preference<Boolean>>()
        val usePinLockPref = mockk<Preference<Boolean>>()
        every { prefs.useAuthenticator() } returns useAuthenticatorPref
        every { useAuthenticatorPref.get() } returns true
        every { prefs.usePinLock() } returns usePinLockPref
        every { usePinLockPref.get() } returns false

        val primaryMethod = AuthenticationOrchestrator.resolvePrimaryMethod(prefs)
        val result = AuthenticationOrchestrator.hasFallbackAvailable(primaryMethod, prefs)

        assertFalse(result)
    }
}
