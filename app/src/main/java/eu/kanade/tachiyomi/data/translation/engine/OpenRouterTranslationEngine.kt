package eu.kanade.tachiyomi.data.translation.engine

/**
 * OpenRouter-compatible translation engine.
 * Uses the OpenAI-compatible API format with OpenRouter's endpoint.
 */
class OpenRouterTranslationEngine(
    apiKey: String,
    model: String = DEFAULT_MODEL,
) : OpenAICompatTranslationEngine(apiKey, model, API_URL) {

    companion object {
        const val DEFAULT_MODEL = "anthropic/claude-sonnet-4-5-20250929"
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }
}
