package eu.kanade.tachiyomi.security

import android.content.SharedPreferences
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrackerTokenMigrationTest {

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
    fun `migrates single tracker token to secure store`() {
        every { mockPrefs.all } returns mapOf("__PRIVATE_track_token_1" to "token_abc")

        TrackerTokenMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe "token_abc"
    }

    @Test
    fun `migrates multiple tracker tokens for different tracker ids`() {
        every { mockPrefs.all } returns mapOf(
            "__PRIVATE_track_token_1" to "token_one",
            "__PRIVATE_track_token_2" to "token_two",
            "__PRIVATE_track_token_42" to "token_fortytwo",
        )

        TrackerTokenMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTrackerToken(1L) shouldBe "token_one"
        RayniyomiSecurePrefs.getTrackerToken(2L) shouldBe "token_two"
        RayniyomiSecurePrefs.getTrackerToken(42L) shouldBe "token_fortytwo"
    }

    @Test
    fun `deletes old plaintext key after migration`() {
        every { mockPrefs.all } returns mapOf("__PRIVATE_track_token_5" to "token_abc")

        TrackerTokenMigration.migrate(mockPrefs)

        verify { mockEditor.remove("__PRIVATE_track_token_5") }
        verify { mockEditor.apply() }
    }

    // Non-token keys are untouched

    @Test
    fun `does not migrate pref_mangasync username keys`() {
        every { mockPrefs.all } returns mapOf(
            "__PRIVATE_pref_mangasync_username_1" to "user@example.com",
        )

        TrackerTokenMigration.migrate(mockPrefs)

        // No tokens written to secure store; no cleanup attempted
        RayniyomiSecurePrefs.getTrackerToken(1L).shouldBeNull()
        verify(exactly = 0) { mockEditor.apply() }
    }

    @Test
    fun `does not migrate pref_mangasync password keys`() {
        every { mockPrefs.all } returns mapOf(
            "__PRIVATE_pref_mangasync_password_1" to "password123",
        )

        TrackerTokenMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTrackerToken(1L).shouldBeNull()
        verify(exactly = 0) { mockEditor.apply() }
    }

    @Test
    fun `migrates only track_token keys from mixed preferences`() {
        every { mockPrefs.all } returns mapOf(
            "__PRIVATE_track_token_3" to "my_token",
            "__PRIVATE_pref_mangasync_username_3" to "user@example.com",
            "__PRIVATE_pref_mangasync_password_3" to "password123",
            "some_other_key" to "some_value",
        )

        TrackerTokenMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTrackerToken(3L) shouldBe "my_token"
        verify { mockEditor.remove("__PRIVATE_track_token_3") }
        verify(exactly = 0) { mockEditor.remove("__PRIVATE_pref_mangasync_username_3") }
        verify(exactly = 0) { mockEditor.remove("__PRIVATE_pref_mangasync_password_3") }
        verify(exactly = 0) { mockEditor.remove("some_other_key") }
    }

    // Skip and idempotency

    @Test
    fun `skips migration when no tracker token keys exist`() {
        every { mockPrefs.all } returns emptyMap()

        TrackerTokenMigration.migrate(mockPrefs)

        verify(exactly = 0) { mockEditor.apply() }
    }

    @Test
    fun `does not overwrite secure store token when already migrated`() {
        RayniyomiSecurePrefs.setTrackerToken(7L, "alreadyMigratedToken")
        every { mockPrefs.all } returns mapOf("__PRIVATE_track_token_7" to "oldPlaintextToken")

        TrackerTokenMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTrackerToken(7L) shouldBe "alreadyMigratedToken"
    }

    @Test
    fun `cleans up plaintext key even when secure store already has token`() {
        RayniyomiSecurePrefs.setTrackerToken(7L, "alreadyMigratedToken")
        every { mockPrefs.all } returns mapOf("__PRIVATE_track_token_7" to "oldPlaintextToken")

        TrackerTokenMigration.migrate(mockPrefs)

        verify { mockEditor.remove("__PRIVATE_track_token_7") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `safe to call multiple times without data loss`() {
        every { mockPrefs.all } returns mapOf("__PRIVATE_track_token_10" to "tokenValue")

        TrackerTokenMigration.migrate(mockPrefs)
        // Second call: plaintext already deleted
        every { mockPrefs.all } returns emptyMap()
        TrackerTokenMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTrackerToken(10L) shouldBe "tokenValue"
    }
}
