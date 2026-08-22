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
        if (provider == TranslationProvider.NONE) return null

        val apiKey = translationPreferences.translationApiKey(provider).get()
        if (apiKey.isBlank()) return null

        val model = translationPreferences.translationModel(provider).get().ifBlank {
            when (provider) {
                TranslationProvider.CLAUDE -> ClaudeTranslationEngine.DEFAULT_MODEL
                TranslationProvider.OPENAI -> OpenAITranslationEngine.DEFAULT_MODEL
                TranslationProvider.OPENROUTER -> return null
                TranslationProvider.GOOGLE -> GoogleTranslationEngine.DEFAULT_MODEL
                TranslationProvider.NONE -> return null
            }
        }

        return when (provider) {
            TranslationProvider.CLAUDE -> ClaudeTranslationEngine(
                apiKey = apiKey,
                model = model,
            )
            TranslationProvider.OPENAI -> OpenAITranslationEngine(
                apiKey = apiKey,
                model = model,
            )
            TranslationProvider.OPENROUTER -> OpenRouterTranslationEngine(
                apiKey = apiKey,
                model = model,
            )
            TranslationProvider.GOOGLE -> GoogleTranslationEngine(
                apiKey = apiKey,
                model = model,
            )
            TranslationProvider.NONE -> null
        }
    }
}
