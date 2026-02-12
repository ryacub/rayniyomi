package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.engine.ClaudeTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.GoogleTranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenAITranslationEngine
import eu.kanade.tachiyomi.data.translation.engine.OpenRouterTranslationEngine

class TranslationEngineFactory(
    private val translationPreferences: TranslationPreferences,
) {

    fun create(): TranslationEngine? {
        val provider = translationPreferences.translationProvider().get()
        val apiKey = translationPreferences.translationApiKey().get()
        if (provider == TranslationProvider.NONE || apiKey.isBlank()) return null

        val model = translationPreferences.translationModel().get()
        return when (provider) {
            TranslationProvider.CLAUDE -> ClaudeTranslationEngine(
                apiKey = apiKey,
                model = model.ifBlank { ClaudeTranslationEngine.DEFAULT_MODEL },
            )
            TranslationProvider.OPENAI -> OpenAITranslationEngine(
                apiKey = apiKey,
                model = model.ifBlank { OpenAITranslationEngine.DEFAULT_MODEL },
            )
            TranslationProvider.OPENROUTER -> OpenRouterTranslationEngine(
                apiKey = apiKey,
                model = model.ifBlank { OpenRouterTranslationEngine.DEFAULT_MODEL },
            )
            TranslationProvider.GOOGLE -> GoogleTranslationEngine(
                apiKey = apiKey,
                model = model.ifBlank { GoogleTranslationEngine.DEFAULT_MODEL },
            )
            TranslationProvider.NONE -> null
        }
    }
}
