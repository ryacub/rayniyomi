package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.engine.ClaudeTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenAITranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslationEngine
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class TranslationEngineFactoryTest {

    private val prefs = mockk<TranslationPreferences>()

    private fun mockProvider(provider: TranslationProvider) {
        val pref = mockk<Preference<TranslationProvider>>()
        every { pref.get() } returns provider
        every { prefs.translationProvider() } returns pref
    }

    private fun mockApiKey(provider: TranslationProvider, key: String) {
        val pref = mockk<Preference<String>>()
        every { pref.get() } returns key
        every { prefs.translationApiKey(provider) } returns pref
    }

    private fun mockModel(provider: TranslationProvider, model: String) {
        val pref = mockk<Preference<String>>()
        every { pref.get() } returns model
        every { prefs.translationModel(provider) } returns pref
    }

    @Test
    fun `returns null when provider is NONE`() {
        mockProvider(TranslationProvider.NONE)

        val factory = TranslationEngineFactory(prefs)
        assertNull(factory.create())
    }

    @Test
    fun `returns null when API key is blank`() {
        mockProvider(TranslationProvider.CLAUDE)
        mockApiKey(TranslationProvider.CLAUDE, "")

        val factory = TranslationEngineFactory(prefs)
        assertNull(factory.create())
    }

    @Test
    fun `returns null when model is blank for every provider`() {
        val factory = TranslationEngineFactory(prefs)

        for (provider in listOf(
            TranslationProvider.CLAUDE,
            TranslationProvider.OPENAI,
            TranslationProvider.OPENROUTER,
            TranslationProvider.GOOGLE,
        )) {
            mockProvider(provider)
            mockApiKey(provider, "test-api-key")
            mockModel(provider, "")
            assertNull(factory.create(), "Expected null for $provider with a blank model")
        }
    }

    @Test
    fun `returns null when API key is whitespace only`() {
        mockProvider(TranslationProvider.OPENAI)
        mockApiKey(TranslationProvider.OPENAI, "   ")

        val factory = TranslationEngineFactory(prefs)
        assertNull(factory.create())
    }

    @Test
    fun `returns ClaudeTranslationEngine for CLAUDE provider`() {
        mockProvider(TranslationProvider.CLAUDE)
        mockApiKey(TranslationProvider.CLAUDE, "test-api-key")
        mockModel(TranslationProvider.CLAUDE, "test-model")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is ClaudeTranslationEngine)
    }

    @Test
    fun `returns OpenAITranslationEngine for OPENAI provider`() {
        mockProvider(TranslationProvider.OPENAI)
        mockApiKey(TranslationProvider.OPENAI, "test-api-key")
        mockModel(TranslationProvider.OPENAI, "test-model")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is OpenAITranslationEngine)
    }

    @Test
    fun `returns OpenRouterTranslationEngine for OPENROUTER provider`() {
        mockProvider(TranslationProvider.OPENROUTER)
        mockApiKey(TranslationProvider.OPENROUTER, "test-api-key")
        mockModel(TranslationProvider.OPENROUTER, "example/free-vision")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is OpenRouterTranslationEngine)
    }

    @Test
    fun `returns GoogleTranslationEngine for GOOGLE provider`() {
        mockProvider(TranslationProvider.GOOGLE)
        mockApiKey(TranslationProvider.GOOGLE, "test-api-key")
        mockModel(TranslationProvider.GOOGLE, "test-model")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is GoogleTranslationEngine)
    }
}
