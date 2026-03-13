package eu.kanade.domain.track.service

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.security.InMemorySecureStorage
import eu.kanade.tachiyomi.security.RayniyomiSecurePrefs
import eu.kanade.tachiyomi.security.SecurePreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TrackPreferencesIntegrationTest {

    private lateinit var secureStorage: InMemorySecureStorage
    private lateinit var trackPreferences: TrackPreferences

    private val tracker1 = mockk<Tracker>().also { every { it.id } returns 1L }
    private val tracker2 = mockk<Tracker>().also { every { it.id } returns 42L }

    @BeforeEach
    fun setup() {
        secureStorage = InMemorySecureStorage()
        RayniyomiSecurePrefs.initForTesting(secureStorage)

        val delegate = InMemoryPreferenceStore()
        val secureStore = SecurePreferenceStore(delegate)
        trackPreferences = TrackPreferences(secureStore)
    }

    // --- trackToken ---

    @Test
    fun `trackToken get returns empty string when not set`() {
        trackPreferences.trackToken(tracker1).get() shouldBe ""
    }

    @Test
    fun `trackToken set stores value in RayniyomiSecurePrefs`() {
        trackPreferences.trackToken(tracker1).set("myToken123")

        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe "myToken123"
    }

    @Test
    fun `trackToken get reads from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.setTrackerToken(1L, "storedToken")

        trackPreferences.trackToken(tracker1).get() shouldBe "storedToken"
    }

    @Test
    fun `trackToken delete clears from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.setTrackerToken(1L, "existingToken")

        trackPreferences.trackToken(tracker1).delete()

        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe null
    }

    @Test
    fun `trackToken isSet returns false when not set`() {
        trackPreferences.trackToken(tracker1).isSet() shouldBe false
    }

    @Test
    fun `trackToken isSet returns true when set`() {
        RayniyomiSecurePrefs.setTrackerToken(1L, "someToken")

        trackPreferences.trackToken(tracker1).isSet() shouldBe true
    }

    @Test
    fun `trackToken set with empty string stores empty string in secure store`() {
        trackPreferences.trackToken(tracker1).set("")

        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe ""
    }

    @Test
    fun `trackToken is isolated per tracker id`() {
        trackPreferences.trackToken(tracker1).set("token1")
        trackPreferences.trackToken(tracker2).set("token42")

        trackPreferences.trackToken(tracker1).get() shouldBe "token1"
        trackPreferences.trackToken(tracker2).get() shouldBe "token42"
    }

    @Test
    fun `trackToken key includes tracker id`() {
        trackPreferences.trackToken(tracker1).key() shouldBe "__PRIVATE_track_token_1"
    }

    @Test
    fun `trackToken defaultValue returns empty string`() {
        trackPreferences.trackToken(tracker1).defaultValue() shouldBe ""
    }

    // --- Delegation: non-token prefs still use the delegate store ---

    @Test
    fun `autoUpdateTrack uses delegate store not secure store`() {
        val pref = trackPreferences.autoUpdateTrack()
        pref.set(false)

        pref.get() shouldBe false
    }
}
