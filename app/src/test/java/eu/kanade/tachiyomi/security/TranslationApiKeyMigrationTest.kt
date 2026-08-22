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
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.commit() } returns true
        mockPrefs = mockk()
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString("translation_provider", null) } returns "CLAUDE"
        every { mockPrefs.getString("translation_api_key", null) } returns null
        every { mockPrefs.getString("translation_model", null) } returns null
        every { mockPrefs.getString("translation_legacy_migration_provider", null) } returns null
        every { mockPrefs.getString(match { it.startsWith("translation_model_") }, null) } returns null
    }

    @Test
    fun `migrates legacy key and model only to selected provider`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_model", null) } returns "legacy-model"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "legacy-key"
        RayniyomiSecurePrefs.getTranslationApiKey("openai").shouldBeNull()
        verify { mockEditor.putString("translation_model_claude", "legacy-model") }
        verify { mockEditor.remove("translation_model") }
        RayniyomiSecurePrefs.translationApiKey.shouldBeNull()
    }

    @Test
    fun `does nothing when no legacy values exist`() {
        TranslationApiKeyMigration.migrate(mockPrefs)

        verify(exactly = 0) { mockPrefs.edit() }
    }

    @Test
    fun `migrates a legacy plaintext key to selected provider`() {
        every { mockPrefs.getString("translation_api_key", null) } returns "plain-key"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "plain-key"
        verify { mockEditor.remove("translation_api_key") }
    }

    @Test
    fun `keeps legacy values when provider is none`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_provider", null) } returns "NONE"
        every { mockPrefs.getString("translation_model", null) } returns "legacy-model"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "legacy-key"
        verify(exactly = 0) { mockEditor.remove(any()) }
    }

    @Test
    fun `keeps legacy values when provider is invalid`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_provider", null) } returns "INVALID"
        every { mockPrefs.getString("translation_model", null) } returns "legacy-model"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "legacy-key"
        verify(exactly = 0) { mockEditor.remove(any()) }
    }

    @Test
    fun `keeps legacy key when selected provider has a different key`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        RayniyomiSecurePrefs.setTranslationApiKey("claude", "existing-key")

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "legacy-key"
        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "existing-key"
        verify(exactly = 0) { mockEditor.remove(any()) }
    }

    @Test
    fun `keeps legacy values when provider key write fails`() {
        val storage = FailingSecureStorage("translation_api_key_claude")
        RayniyomiSecurePrefs.initForTesting(storage)
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_model", null) } returns "legacy-model"

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "legacy-key"
        verify(exactly = 0) { mockEditor.remove(any()) }
    }

    @Test
    fun `keeps legacy values when model write fails`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_model", null) } returns "legacy-model"
        every { mockEditor.commit() } returnsMany listOf(true, false)

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "legacy-key"
        verify(exactly = 0) { mockEditor.remove("translation_model") }
    }

    @Test
    fun `restores legacy secure key when cleanup fails`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_model", null) } returns "legacy-model"
        every { mockEditor.commit() } returnsMany listOf(true, true, false)

        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.translationApiKey shouldBe "legacy-key"
        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "legacy-key"
        verify { mockEditor.remove("translation_model") }
    }

    @Test
    fun `migration is idempotent`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"

        TranslationApiKeyMigration.migrate(mockPrefs)
        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "legacy-key"
    }

    @Test
    fun `retry keeps the provider pinned after selection changes`() {
        val storage = FailingSecureStorage("translation_api_key_claude")
        RayniyomiSecurePrefs.initForTesting(storage)
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"

        TranslationApiKeyMigration.migrate(mockPrefs)
        every { mockPrefs.getString("translation_legacy_migration_provider", null) } returns "CLAUDE"
        every { mockPrefs.getString("translation_provider", null) } returns "OPENAI"
        storage.failingKey = null
        TranslationApiKeyMigration.migrate(mockPrefs)

        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "legacy-key"
        RayniyomiSecurePrefs.getTranslationApiKey("openai").shouldBeNull()
    }

    @Test
    fun `background migration uses the provider captured at startup`() {
        RayniyomiSecurePrefs.translationApiKey = "legacy-key"
        every { mockPrefs.getString("translation_provider", null) } returns "OPENAI"

        TranslationApiKeyMigration.migrate(mockPrefs, providerAtStartup = "CLAUDE")

        RayniyomiSecurePrefs.getTranslationApiKey("claude") shouldBe "legacy-key"
        RayniyomiSecurePrefs.getTranslationApiKey("openai").shouldBeNull()
    }

    private class FailingSecureStorage(
        var failingKey: String?,
    ) : SecureStorage {
        private val data = mutableMapOf<String, String>()

        override fun getString(key: String): String? = data[key]

        override fun putString(key: String, value: String?) {
            if (key == failingKey) error("Test write failure")
            if (value == null) data.remove(key) else data[key] = value
        }
    }
}
