package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.security.InMemorySecureStorage
import eu.kanade.tachiyomi.security.RayniyomiSecurePrefs
import eu.kanade.tachiyomi.security.SecurePreferenceStore
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TranslationPreferencesIntegrationTest {

    private lateinit var delegate: InMemoryPreferenceStore
    private lateinit var translationPreferences: TranslationPreferences

    @BeforeEach
    fun setup() {
        RayniyomiSecurePrefs.initForTesting(InMemorySecureStorage())

        delegate = InMemoryPreferenceStore()
        translationPreferences = TranslationPreferences(SecurePreferenceStore(delegate))
    }

    @Test
    fun `translationApiKey get returns empty string when not set`() {
        translationPreferences.translationApiKey().get() shouldBe ""
    }

    @Test
    fun `translationApiKey set stores value in RayniyomiSecurePrefs`() {
        translationPreferences.translationApiKey().set("provider-key")

        RayniyomiSecurePrefs.translationApiKey shouldBe "provider-key"
    }

    @Test
    fun `translationApiKey get reads from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.translationApiKey = "stored-provider-key"

        translationPreferences.translationApiKey().get() shouldBe "stored-provider-key"
    }

    @Test
    fun `translationApiKey delete clears from RayniyomiSecurePrefs`() {
        RayniyomiSecurePrefs.translationApiKey = "existing-provider-key"

        translationPreferences.translationApiKey().delete()

        RayniyomiSecurePrefs.translationApiKey shouldBe null
    }

    @Test
    fun `translationApiKey key returns translation_api_key`() {
        translationPreferences.translationApiKey().key() shouldBe "translation_api_key"
    }

    @Test
    fun `targetLanguage still uses delegate store`() {
        val targetLanguage = translationPreferences.targetLanguage()

        targetLanguage.set("ja")

        targetLanguage.get() shouldBe "ja"
        RayniyomiSecurePrefs.translationApiKey shouldBe null
    }
}
