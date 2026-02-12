package eu.kanade.tachiyomi.data.translation.engine

class OpenAITranslationEngine(
    apiKey: String,
    model: String = DEFAULT_MODEL,
) : OpenAICompatTranslationEngine(apiKey, model, API_URL) {

    companion object {
        const val DEFAULT_MODEL = "gpt-4o"
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
    }
}
