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

    private fun mockApiKey(key: String) {
        val pref = mockk<Preference<String>>()
        every { pref.get() } returns key
        every { prefs.translationApiKey() } returns pref
    }

    private fun mockModel(model: String) {
        val pref = mockk<Preference<String>>()
        every { pref.get() } returns model
        every { prefs.translationModel() } returns pref
    }

    @Test
    fun `returns null when provider is NONE`() {
        mockProvider(TranslationProvider.NONE)
        mockApiKey("some-key")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        assertNull(factory.create())
    }

    @Test
    fun `returns null when API key is blank`() {
        mockProvider(TranslationProvider.CLAUDE)
        mockApiKey("")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        assertNull(factory.create())
    }

    @Test
    fun `returns null when API key is whitespace only`() {
        mockProvider(TranslationProvider.OPENAI)
        mockApiKey("   ")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        assertNull(factory.create())
    }

    @Test
    fun `returns ClaudeTranslationEngine for CLAUDE provider`() {
        mockProvider(TranslationProvider.CLAUDE)
        mockApiKey("test-api-key")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is ClaudeTranslationEngine)
    }

    @Test
    fun `returns OpenAITranslationEngine for OPENAI provider`() {
        mockProvider(TranslationProvider.OPENAI)
        mockApiKey("test-api-key")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is OpenAITranslationEngine)
    }

    @Test
    fun `returns OpenRouterTranslationEngine for OPENROUTER provider`() {
        mockProvider(TranslationProvider.OPENROUTER)
        mockApiKey("test-api-key")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is OpenRouterTranslationEngine)
    }

    @Test
    fun `returns GoogleTranslationEngine for GOOGLE provider`() {
        mockProvider(TranslationProvider.GOOGLE)
        mockApiKey("test-api-key")
        mockModel("")

        val factory = TranslationEngineFactory(prefs)
        val engine = factory.create()
        assertTrue(engine is GoogleTranslationEngine)
    }
}
