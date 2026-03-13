package eu.kanade.tachiyomi.core.security

import eu.kanade.tachiyomi.security.InMemorySecureStorage
import eu.kanade.tachiyomi.security.RayniyomiSecurePrefs
import eu.kanade.tachiyomi.security.SecurePreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class SecurityPreferencesIntegrationTest {

    private lateinit var secureStorage: InMemorySecureStorage
    private lateinit var securityPreferences: SecurityPreferences

    @BeforeEach
    fun setup() {
        secureStorage = InMemorySecureStorage()
        RayniyomiSecurePrefs.initForTesting(secureStorage)

        val delegate = InMemoryPreferenceStore()
        val secureStore = SecurePreferenceStore(delegate)
        securityPreferences = SecurityPreferences(secureStore)
    }

    // --- pinHash ---

    @Test
    fun `pinHash get returns empty string when not set`() {
        securityPreferences.pinHash().get() shouldBe ""
    }

    @Test
    fun `pinHash set stores value in RayniyomiSecurePrefs`() {
        securityPreferences.pinHash().set("hashedPin123")

        RayniyomiSecurePrefs.pinHash shouldBe "hashedPin123"
    }

    @Test
    fun `pinHash get reads from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.pinHash = "storedHash"

        securityPreferences.pinHash().get() shouldBe "storedHash"
    }

    @Test
    fun `pinHash delete clears from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.pinHash = "existingHash"

        securityPreferences.pinHash().delete()

        RayniyomiSecurePrefs.pinHash shouldBe null
    }

    @Test
    fun `pinHash isSet returns false when not set`() {
        securityPreferences.pinHash().isSet() shouldBe false
    }

    @Test
    fun `pinHash isSet returns true when set`() {
        RayniyomiSecurePrefs.pinHash = "someHash"

        securityPreferences.pinHash().isSet() shouldBe true
    }

    @Test
    fun `pinHash set with empty string clears from secure store`() {
        RayniyomiSecurePrefs.pinHash = "existingHash"

        securityPreferences.pinHash().set("")

        RayniyomiSecurePrefs.pinHash shouldBe null
    }

    @Test
    fun `pinHash key returns pin_hash`() {
        securityPreferences.pinHash().key() shouldBe "pin_hash"
    }

    @Test
    fun `pinHash defaultValue returns empty string`() {
        securityPreferences.pinHash().defaultValue() shouldBe ""
    }

    // --- pinSalt ---

    @Test
    fun `pinSalt get returns empty string when not set`() {
        securityPreferences.pinSalt().get() shouldBe ""
    }

    @Test
    fun `pinSalt set stores value in RayniyomiSecurePrefs`() {
        securityPreferences.pinSalt().set("saltValue123")

        RayniyomiSecurePrefs.pinSalt shouldBe "saltValue123"
    }

    @Test
    fun `pinSalt get reads from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.pinSalt = "storedSalt"

        securityPreferences.pinSalt().get() shouldBe "storedSalt"
    }

    @Test
    fun `pinSalt delete clears from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.pinSalt = "existingSalt"

        securityPreferences.pinSalt().delete()

        RayniyomiSecurePrefs.pinSalt shouldBe null
    }

    @Test
    fun `pinSalt isSet returns false when not set`() {
        securityPreferences.pinSalt().isSet() shouldBe false
    }

    @Test
    fun `pinSalt isSet returns true when set`() {
        RayniyomiSecurePrefs.pinSalt = "someSalt"

        securityPreferences.pinSalt().isSet() shouldBe true
    }

    // --- Delegation: non-secure prefs still use the delegate store ---

    @Test
    fun `usePinLock uses delegate store not secure store`() {
        val pref = securityPreferences.usePinLock()
        pref.set(true)

        pref.get() shouldBe true
    }
}
