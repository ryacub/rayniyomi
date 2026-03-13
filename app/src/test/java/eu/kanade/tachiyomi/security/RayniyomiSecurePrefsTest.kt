package eu.kanade.tachiyomi.security

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RayniyomiSecurePrefsTest {

    private lateinit var storage: InMemorySecureStorage

    @BeforeEach
    fun setup() {
        storage = InMemorySecureStorage()
        RayniyomiSecurePrefs.initForTesting(storage)
    }

    // PIN hash tests

    @Test
    fun `stores and retrieves PIN hash successfully`() {
        RayniyomiSecurePrefs.pinHash = "hashedPinValue123"
        RayniyomiSecurePrefs.pinHash shouldBe "hashedPinValue123"
    }

    @Test
    fun `returns null PIN hash when not set`() {
        RayniyomiSecurePrefs.pinHash.shouldBeNull()
    }

    @Test
    fun `stores and retrieves empty string PIN hash`() {
        RayniyomiSecurePrefs.pinHash = ""
        RayniyomiSecurePrefs.pinHash shouldBe ""
    }

    @Test
    fun `clears PIN hash when set to null`() {
        RayniyomiSecurePrefs.pinHash = "initialHash"
        RayniyomiSecurePrefs.pinHash = null
        RayniyomiSecurePrefs.pinHash.shouldBeNull()
    }

    @Test
    fun `overwrites existing PIN hash`() {
        RayniyomiSecurePrefs.pinHash = "firstHash"
        RayniyomiSecurePrefs.pinHash = "secondHash"
        RayniyomiSecurePrefs.pinHash shouldBe "secondHash"
    }

    @Test
    fun `handles long PIN hash values`() {
        val longHash = "a".repeat(1000)
        RayniyomiSecurePrefs.pinHash = longHash
        RayniyomiSecurePrefs.pinHash shouldBe longHash
    }

    @Test
    fun `handles PIN hash with special characters`() {
        val specialHash = "!@#$%^&*()_+-=[]{}|;:',.<>?/`~"
        RayniyomiSecurePrefs.pinHash = specialHash
        RayniyomiSecurePrefs.pinHash shouldBe specialHash
    }

    // PIN salt tests

    @Test
    fun `stores and retrieves PIN salt successfully`() {
        RayniyomiSecurePrefs.pinSalt = "saltValue123"
        RayniyomiSecurePrefs.pinSalt shouldBe "saltValue123"
    }

    @Test
    fun `returns null PIN salt when not set`() {
        RayniyomiSecurePrefs.pinSalt.shouldBeNull()
    }

    @Test
    fun `clears PIN salt when set to null`() {
        RayniyomiSecurePrefs.pinSalt = "someSalt"
        RayniyomiSecurePrefs.pinSalt = null
        RayniyomiSecurePrefs.pinSalt.shouldBeNull()
    }

    // Tracker token tests

    @Test
    fun `stores and retrieves tracker token for specific trackerId`() {
        RayniyomiSecurePrefs.setTrackerToken(1L, "tracker_token_abc123")
        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe "tracker_token_abc123"
    }

    @Test
    fun `returns null tracker token when not set`() {
        RayniyomiSecurePrefs.getTrackerToken(42L).shouldBeNull()
    }

    @Test
    fun `stores and retrieves empty string tracker token`() {
        RayniyomiSecurePrefs.setTrackerToken(5L, "")
        RayniyomiSecurePrefs.getTrackerToken(5L) shouldBe ""
    }

    @Test
    fun `clears tracker token when set to null`() {
        RayniyomiSecurePrefs.setTrackerToken(10L, "tokenValue")
        RayniyomiSecurePrefs.setTrackerToken(10L, null)
        RayniyomiSecurePrefs.getTrackerToken(10L).shouldBeNull()
    }

    @Test
    fun `stores multiple tracker tokens for different trackerIds without collision`() {
        RayniyomiSecurePrefs.setTrackerToken(1L, "token_for_tracker_1")
        RayniyomiSecurePrefs.setTrackerToken(2L, "token_for_tracker_2")
        RayniyomiSecurePrefs.setTrackerToken(100L, "token_for_tracker_3")

        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe "token_for_tracker_1"
        RayniyomiSecurePrefs.getTrackerToken(2L) shouldBe "token_for_tracker_2"
        RayniyomiSecurePrefs.getTrackerToken(100L) shouldBe "token_for_tracker_3"
    }

    @Test
    fun `overwrites existing tracker token for same trackerId`() {
        RayniyomiSecurePrefs.setTrackerToken(7L, "firstToken")
        RayniyomiSecurePrefs.setTrackerToken(7L, "secondToken")
        RayniyomiSecurePrefs.getTrackerToken(7L) shouldBe "secondToken"
    }

    @Test
    fun `handles tracker token with long values`() {
        val longToken = "x".repeat(2000)
        RayniyomiSecurePrefs.setTrackerToken(15L, longToken)
        RayniyomiSecurePrefs.getTrackerToken(15L) shouldBe longToken
    }

    @Test
    fun `handles tracker token with special characters`() {
        val specialToken = "!@#$%^&*()_+-=[]{}|;:',.<>?/`~\n\t"
        RayniyomiSecurePrefs.setTrackerToken(20L, specialToken)
        RayniyomiSecurePrefs.getTrackerToken(20L) shouldBe specialToken
    }

    @Test
    fun `handles tracker token for trackerId with zero value`() {
        RayniyomiSecurePrefs.setTrackerToken(0L, "token_for_zero")
        RayniyomiSecurePrefs.getTrackerToken(0L) shouldBe "token_for_zero"
    }

    @Test
    fun `handles tracker token for trackerId with negative value`() {
        RayniyomiSecurePrefs.setTrackerToken(-1L, "token_for_negative")
        RayniyomiSecurePrefs.getTrackerToken(-1L) shouldBe "token_for_negative"
    }

    @Test
    fun `handles tracker token for trackerId with max long value`() {
        RayniyomiSecurePrefs.setTrackerToken(Long.MAX_VALUE, "token_for_max")
        RayniyomiSecurePrefs.getTrackerToken(Long.MAX_VALUE) shouldBe "token_for_max"
    }

    // Mixed operations

    @Test
    fun `PIN hash and tracker tokens are stored independently`() {
        RayniyomiSecurePrefs.pinHash = "pinHashValue"
        RayniyomiSecurePrefs.setTrackerToken(1L, "trackerToken123")

        RayniyomiSecurePrefs.pinHash shouldBe "pinHashValue"
        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe "trackerToken123"
    }

    @Test
    fun `clearing PIN hash does not affect tracker tokens`() {
        RayniyomiSecurePrefs.pinHash = "pinHashValue"
        RayniyomiSecurePrefs.setTrackerToken(5L, "trackerToken")

        RayniyomiSecurePrefs.pinHash = null

        RayniyomiSecurePrefs.pinHash.shouldBeNull()
        RayniyomiSecurePrefs.getTrackerToken(5L) shouldBe "trackerToken"
    }

    @Test
    fun `clearing tracker token does not affect PIN hash`() {
        RayniyomiSecurePrefs.pinHash = "pinHashValue"
        RayniyomiSecurePrefs.setTrackerToken(3L, "trackerToken")

        RayniyomiSecurePrefs.setTrackerToken(3L, null)

        RayniyomiSecurePrefs.pinHash shouldBe "pinHashValue"
        RayniyomiSecurePrefs.getTrackerToken(3L).shouldBeNull()
    }

    // Storage isolation (key name verification)

    @Test
    fun `PIN hash uses correct key in storage`() {
        RayniyomiSecurePrefs.pinHash = "pinValue"
        storage.getString("pin_hash") shouldBe "pinValue"
    }

    @Test
    fun `PIN salt uses correct key in storage`() {
        RayniyomiSecurePrefs.pinSalt = "saltValue"
        storage.getString("pin_salt") shouldBe "saltValue"
    }

    @Test
    fun `tracker tokens use prefixed keys in storage`() {
        RayniyomiSecurePrefs.setTrackerToken(99L, "tokenValue")
        storage.getString("track_token_99") shouldBe "tokenValue"
    }

    @Test
    fun `tracker token keys are unique per trackerId`() {
        RayniyomiSecurePrefs.setTrackerToken(1L, "token1")
        RayniyomiSecurePrefs.setTrackerToken(2L, "token2")

        storage.getString("track_token_1") shouldBe "token1"
        storage.getString("track_token_2") shouldBe "token2"
    }
}
