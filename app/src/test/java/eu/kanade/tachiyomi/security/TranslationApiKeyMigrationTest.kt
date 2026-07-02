package eu.kanade.tachiyomi.security

import android.content.SharedPreferences
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranslationApiKeyMigrationTest {

    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockPrefs: SharedPreferences

    @BeforeEach
    fun setup() {
        RayniyomiSecurePrefs.initForTesting(InMemorySecureStorage())

        mockEditor = mockk(relaxed = true)
        every { mockEditor.remove(any()) } returns mockEditor
        mockPrefs = mockk()
        every { mockPrefs.edit() } returns mockEditor
    }

    @Test
    fun `migrates translation api key from plaintext to secure store`() {
        every { mockPrefs.getString("translation_api_key", null) } returns "plain-provider-key"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "plain-provider-key"
    }

    @Test
    fun `deletes old plaintext key after migration`() {
        every { mockPrefs.getString("translation_api_key", null) } returns "plain-provider-key"

        TranslationApiKeyMigration.migrate(mockPrefs)

        verify { mockEditor.remove("translation_api_key") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `skips migration when no plaintext api key exists`() {
        every { mockPrefs.getString("translation_api_key", null) } returns null

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey.shouldBeNull()
        verify(exactly = 0) { mockEditor.apply() }
    }

    @Test
    fun `skips migration when plaintext api key is empty string`() {
        every { mockPrefs.getString("translation_api_key", null) } returns ""

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey.shouldBeNull()
        verify(exactly = 0) { mockEditor.apply() }
    }

    @Test
    fun `does not overwrite secure store when already migrated`() {
        RayniyomiSecurePrefs.translationApiKey = "already-secure-key"
        every { mockPrefs.getString("translation_api_key", null) } returns "old-plain-key"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "already-secure-key"
    }

    @Test
    fun `overwrites empty secure store with plaintext api key`() {
        RayniyomiSecurePrefs.translationApiKey = ""
        every { mockPrefs.getString("translation_api_key", null) } returns "plain-provider-key"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "plain-provider-key"
    }

    @Test
    fun `cleans up plaintext key even when secure store already has value`() {
        RayniyomiSecurePrefs.translationApiKey = "already-secure-key"
        every { mockPrefs.getString("translation_api_key", null) } returns "old-plain-key"

        TranslationApiKeyMigration.migrate(mockPrefs)

        verify { mockEditor.remove("translation_api_key") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `safe to call multiple times without data loss`() {
        every { mockPrefs.getString("translation_api_key", null) } returns "plain-provider-key"

        TranslationApiKeyMigration.migrate(mockPrefs)
        every { mockPrefs.getString("translation_api_key", null) } returns null
        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "plain-provider-key"
    }
}
