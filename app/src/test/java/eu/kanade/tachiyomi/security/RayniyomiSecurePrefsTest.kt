package eu.kanade.tachiyomi.security

import android.content.SharedPreferences
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RayniyomiSecurePrefsTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @BeforeEach
    fun setup() {
        fakePrefs = FakeSharedPreferences()
        RayniyomiSecurePrefs.initForTesting(fakePrefs)
    }

    // PIN hash tests (happy path and edge cases)

    @Test
    fun `stores and retrieves PIN hash successfully`() {
        val pinHash = "hashedPinValue123"

        RayniyomiSecurePrefs.pinHash = pinHash

        RayniyomiSecurePrefs.pinHash shouldBe pinHash
    }

    @Test
    fun `returns null PIN hash when not set`() {
        RayniyomiSecurePrefs.pinHash.shouldBeNull()
    }

    @Test
    fun `stores and retrieves empty string PIN hash`() {
        val emptyHash = ""

        RayniyomiSecurePrefs.pinHash = emptyHash

        RayniyomiSecurePrefs.pinHash shouldBe emptyHash
    }

    @Test
    fun `clears PIN hash when set to null`() {
        val initialHash = "initialHash"
        RayniyomiSecurePrefs.pinHash = initialHash

        RayniyomiSecurePrefs.pinHash = null

        RayniyomiSecurePrefs.pinHash.shouldBeNull()
    }

    @Test
    fun `overwrites existing PIN hash`() {
        val firstHash = "firstHash"
        val secondHash = "secondHash"
        RayniyomiSecurePrefs.pinHash = firstHash

        RayniyomiSecurePrefs.pinHash = secondHash

        RayniyomiSecurePrefs.pinHash shouldBe secondHash
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

    // Tracker token tests (happy path and edge cases)

    @Test
    fun `stores and retrieves tracker token for specific trackerId`() {
        val trackerId = 1L
        val token = "tracker_token_abc123"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe token
    }

    @Test
    fun `returns null tracker token when not set`() {
        val trackerId = 42L

        RayniyomiSecurePrefs.getTrackerToken(trackerId).shouldBeNull()
    }

    @Test
    fun `stores and retrieves empty string tracker token`() {
        val trackerId = 5L
        val emptyToken = ""

        RayniyomiSecurePrefs.setTrackerToken(trackerId, emptyToken)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe emptyToken
    }

    @Test
    fun `clears tracker token when set to null`() {
        val trackerId = 10L
        val token = "tokenValue"
        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.setTrackerToken(trackerId, null)

        RayniyomiSecurePrefs.getTrackerToken(trackerId).shouldBeNull()
    }

    @Test
    fun `stores multiple tracker tokens for different trackerIds without collision`() {
        val trackerId1 = 1L
        val trackerId2 = 2L
        val trackerId3 = 100L
        val token1 = "token_for_tracker_1"
        val token2 = "token_for_tracker_2"
        val token3 = "token_for_tracker_3"

        RayniyomiSecurePrefs.setTrackerToken(trackerId1, token1)
        RayniyomiSecurePrefs.setTrackerToken(trackerId2, token2)
        RayniyomiSecurePrefs.setTrackerToken(trackerId3, token3)

        RayniyomiSecurePrefs.getTrackerToken(trackerId1) shouldBe token1
        RayniyomiSecurePrefs.getTrackerToken(trackerId2) shouldBe token2
        RayniyomiSecurePrefs.getTrackerToken(trackerId3) shouldBe token3
    }

    @Test
    fun `overwrites existing tracker token for same trackerId`() {
        val trackerId = 7L
        val firstToken = "firstToken"
        val secondToken = "secondToken"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, firstToken)
        RayniyomiSecurePrefs.setTrackerToken(trackerId, secondToken)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe secondToken
    }

    @Test
    fun `handles tracker token with long values`() {
        val trackerId = 15L
        val longToken = "x".repeat(2000)

        RayniyomiSecurePrefs.setTrackerToken(trackerId, longToken)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe longToken
    }

    @Test
    fun `handles tracker token with special characters`() {
        val trackerId = 20L
        val specialToken = "!@#$%^&*()_+-=[]{}|;:',.<>?/`~\n\t"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, specialToken)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe specialToken
    }

    @Test
    fun `handles tracker token for trackerId with zero value`() {
        val trackerId = 0L
        val token = "token_for_zero"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe token
    }

    @Test
    fun `handles tracker token for trackerId with negative value`() {
        val trackerId = -1L
        val token = "token_for_negative"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe token
    }

    @Test
    fun `handles tracker token for trackerId with max long value`() {
        val trackerId = Long.MAX_VALUE
        val token = "token_for_max"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe token
    }

    // PIN hash and tracker tokens together (mixed operations)

    @Test
    fun `PIN hash and tracker tokens are stored independently`() {
        val pinHash = "pinHashValue"
        val trackerId = 1L
        val token = "trackerToken123"

        RayniyomiSecurePrefs.pinHash = pinHash
        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.pinHash shouldBe pinHash
        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe token
    }

    @Test
    fun `clearing PIN hash does not affect tracker tokens`() {
        val pinHash = "pinHashValue"
        val trackerId = 5L
        val token = "trackerToken"

        RayniyomiSecurePrefs.pinHash = pinHash
        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.pinHash = null

        RayniyomiSecurePrefs.pinHash.shouldBeNull()
        RayniyomiSecurePrefs.getTrackerToken(trackerId) shouldBe token
    }

    @Test
    fun `clearing tracker token does not affect PIN hash`() {
        val pinHash = "pinHashValue"
        val trackerId = 3L
        val token = "trackerToken"

        RayniyomiSecurePrefs.pinHash = pinHash
        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        RayniyomiSecurePrefs.setTrackerToken(trackerId, null)

        RayniyomiSecurePrefs.pinHash shouldBe pinHash
        RayniyomiSecurePrefs.getTrackerToken(trackerId).shouldBeNull()
    }

    // Storage isolation tests (ensures key names don't collide)

    @Test
    fun `PIN hash uses unique key in storage`() {
        val pinHash = "pinValue"

        RayniyomiSecurePrefs.pinHash = pinHash

        // Verify the key used in the underlying prefs is correct
        fakePrefs.getString("pin_hash", null) shouldBe pinHash
    }

    @Test
    fun `tracker tokens use prefixed keys in storage`() {
        val trackerId = 99L
        val token = "tokenValue"

        RayniyomiSecurePrefs.setTrackerToken(trackerId, token)

        // Verify the key used in the underlying prefs matches pattern
        fakePrefs.getString("track_token_99", null) shouldBe token
    }

    @Test
    fun `tracker token keys are unique per trackerId`() {
        val trackerId1 = 1L
        val trackerId2 = 2L
        val token1 = "token1"
        val token2 = "token2"

        RayniyomiSecurePrefs.setTrackerToken(trackerId1, token1)
        RayniyomiSecurePrefs.setTrackerToken(trackerId2, token2)

        fakePrefs.getString("track_token_1", null) shouldBe token1
        fakePrefs.getString("track_token_2", null) shouldBe token2
    }

    /**
     * Simple in-memory implementation of SharedPreferences for testing.
     * No encryption needed for unit tests; real encryption is tested via integration tests on Android.
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = data.toMap()

        override fun getString(key: String, defValue: String?): String? {
            return (data[key] as? String) ?: defValue
        }

        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
            return (data[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String, defValue: Int): Int {
            return (data[key] as? Int) ?: defValue
        }

        override fun getLong(key: String, defValue: Long): Long {
            return (data[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String, defValue: Float): Float {
            return (data[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean {
            return (data[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String): Boolean = key in data

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

        inner class FakeEditor : SharedPreferences.Editor {
            private val pendingChanges = mutableMapOf<String, Any?>()
            private val keysToRemove = mutableSetOf<String>()

            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                pendingChanges[key] = value
                keysToRemove.remove(key)
                return this
            }

            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
                pendingChanges[key] = values
                keysToRemove.remove(key)
                return this
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                pendingChanges[key] = value
                keysToRemove.remove(key)
                return this
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                pendingChanges[key] = value
                keysToRemove.remove(key)
                return this
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
                pendingChanges[key] = value
                keysToRemove.remove(key)
                return this
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                pendingChanges[key] = value
                keysToRemove.remove(key)
                return this
            }

            override fun remove(key: String): SharedPreferences.Editor {
                pendingChanges.remove(key)
                keysToRemove.add(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                pendingChanges.clear()
                keysToRemove.addAll(data.keys)
                return this
            }

            override fun commit(): Boolean {
                keysToRemove.forEach { data.remove(it) }
                data.putAll(pendingChanges)
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
