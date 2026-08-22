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
    fun `fresh install has no provider api keys`() {
        translationPreferences.translationProvider().get() shouldBe TranslationProvider.NONE
        TranslationProvider.entries
            .filterNot { it == TranslationProvider.NONE }
            .forEach { provider ->
                translationPreferences.translationApiKey(provider).get() shouldBe ""
                translationPreferences.translationModel(provider).get() shouldBe ""
            }
    }

    @Test
    fun `provider api keys are isolated`() {
        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).set("claude-key")
        translationPreferences.translationApiKey(TranslationProvider.OPENAI).set("openai-key")

        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).get() shouldBe "claude-key"
        translationPreferences.translationApiKey(TranslationProvider.OPENAI).get() shouldBe "openai-key"
        translationPreferences.translationApiKey(TranslationProvider.GOOGLE).get() shouldBe ""
    }

    @Test
    fun `provider models are isolated`() {
        val claudeModel = translationPreferences.translationModel(TranslationProvider.CLAUDE)
        val openAiModel = translationPreferences.translationModel(TranslationProvider.OPENAI)
        val googleModel = translationPreferences.translationModel(TranslationProvider.GOOGLE)
        claudeModel.set("claude-model")
        openAiModel.set("openai-model")

        claudeModel.get() shouldBe "claude-model"
        openAiModel.get() shouldBe "openai-model"
        googleModel.get() shouldBe ""
    }

    @Test
    fun `deleting one provider api key keeps other provider keys`() {
        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).set("claude-key")
        translationPreferences.translationApiKey(TranslationProvider.OPENAI).set("openai-key")

        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).delete()

        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).get() shouldBe ""
        translationPreferences.translationApiKey(TranslationProvider.OPENAI).get() shouldBe "openai-key"
    }

    @Test
    fun `provider preference keys use stable provider ids`() {
        translationPreferences.translationApiKey(TranslationProvider.CLAUDE).key() shouldBe
            "translation_api_key_claude"
        translationPreferences.translationModel(TranslationProvider.GOOGLE).key() shouldBe
            "translation_model_google"
    }

    @Test
    fun `each provider has official HTTPS account links`() {
        TranslationProvider.entries
            .filterNot { it == TranslationProvider.NONE }
            .forEach { provider ->
                val links = requireNotNull(provider.links)
                listOf(links.key, links.usage, links.billing, links.revocation)
                    .forEach { link -> link.startsWith("https://") shouldBe true }
            }
    }

    @Test
    fun `targetLanguage still uses delegate store`() {
        val targetLanguage = translationPreferences.targetLanguage()

        targetLanguage.set("ja")

        targetLanguage.get() shouldBe "ja"
        RayniyomiSecurePrefs.translationApiKey shouldBe null
    }
}
