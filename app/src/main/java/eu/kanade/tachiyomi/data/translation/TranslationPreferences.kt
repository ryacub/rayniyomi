package eu.kanade.tachiyomi.data.translation

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun translationProvider() = preferenceStore.getEnum(
        "translation_provider",
        TranslationProvider.NONE,
    )

    fun translationApiKey() = preferenceStore.getString("translation_api_key", "")

    fun targetLanguage() = preferenceStore.getString("translation_target_language", "en")

    fun translationModel() = preferenceStore.getString("translation_model", "")
}

enum class TranslationProvider(val displayName: String) {
    NONE("None"),
    CLAUDE("Claude"),
    OPENAI("OpenAI"),
    OPENROUTER("OpenRouter"),
    GOOGLE("Google Gemini"),
}
