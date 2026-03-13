package eu.kanade.tachiyomi.security

import android.content.SharedPreferences
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PinHashMigrationTest {

    private lateinit var secureStorage: InMemorySecureStorage
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockPrefs: SharedPreferences

    @BeforeEach
    fun setup() {
        secureStorage = InMemorySecureStorage()
        RayniyomiSecurePrefs.initForTesting(secureStorage)

        mockEditor = mockk(relaxed = true)
        every { mockEditor.remove(any()) } returns mockEditor
        mockPrefs = mockk()
        every { mockPrefs.edit() } returns mockEditor
    }

    // Happy path

    @Test
    fun `migrates pin hash from plaintext to secure store`() {
        every { mockPrefs.getString("pin_hash", null) } returns "hashedValue123"
        every { mockPrefs.getString("pin_salt", null) } returns null

        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinHash shouldBe "hashedValue123"
    }

    @Test
    fun `migrates pin salt when present alongside hash`() {
        every { mockPrefs.getString("pin_hash", null) } returns "hashedValue"
        every { mockPrefs.getString("pin_salt", null) } returns "saltValue123"

        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinSalt shouldBe "saltValue123"
    }

    @Test
    fun `does not set pin salt when not present in plaintext`() {
        every { mockPrefs.getString("pin_hash", null) } returns "hashedValue"
        every { mockPrefs.getString("pin_salt", null) } returns null

        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinSalt.shouldBeNull()
    }

    @Test
    fun `deletes old pin_hash key from plaintext after migration`() {
        every { mockPrefs.getString("pin_hash", null) } returns "hashedValue"
        every { mockPrefs.getString("pin_salt", null) } returns null

        PinHashMigration.migrate(mockPrefs)

        verify { mockEditor.remove("pin_hash") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `deletes old pin_salt key from plaintext after migration`() {
        every { mockPrefs.getString("pin_hash", null) } returns "hashedValue"
        every { mockPrefs.getString("pin_salt", null) } returns "saltValue"

        PinHashMigration.migrate(mockPrefs)

        verify { mockEditor.remove("pin_salt") }
        verify { mockEditor.apply() }
    }

    // Skip conditions

    @Test
    fun `skips migration when no plaintext pin hash exists`() {
        every { mockPrefs.getString("pin_hash", null) } returns null

        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinHash.shouldBeNull()
        verify(exactly = 0) { mockEditor.apply() }
    }

    @Test
    fun `skips migration when plaintext pin hash is empty string`() {
        every { mockPrefs.getString("pin_hash", null) } returns ""

        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinHash.shouldBeNull()
        verify(exactly = 0) { mockEditor.apply() }
    }

    // Idempotency

    @Test
    fun `does not overwrite secure store when already migrated`() {
        RayniyomiSecurePrefs.pinHash = "alreadyMigratedHash"
        every { mockPrefs.getString("pin_hash", null) } returns "oldPlaintextValue"
        every { mockPrefs.getString("pin_salt", null) } returns null

        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinHash shouldBe "alreadyMigratedHash"
    }

    @Test
    fun `cleans up plaintext keys even when secure store already has value`() {
        RayniyomiSecurePrefs.pinHash = "alreadyMigratedHash"
        every { mockPrefs.getString("pin_hash", null) } returns "oldPlaintextValue"
        every { mockPrefs.getString("pin_salt", null) } returns null

        PinHashMigration.migrate(mockPrefs)

        verify { mockEditor.remove("pin_hash") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `safe to call multiple times without data loss`() {
        every { mockPrefs.getString("pin_hash", null) } returns "hashedValue"
        every { mockPrefs.getString("pin_salt", null) } returns "saltValue"

        PinHashMigration.migrate(mockPrefs)
        // Second call simulates: plaintext already deleted, secure store has value
        every { mockPrefs.getString("pin_hash", null) } returns null
        PinHashMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.pinHash shouldBe "hashedValue"
        RayniyomiSecurePrefs.pinSalt shouldBe "saltValue"
    }
}
