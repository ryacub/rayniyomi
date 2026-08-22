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

    fun translationApiKey(provider: TranslationProvider) =
        preferenceStore.getString("translation_api_key_${provider.preferenceId}", "")

    fun targetLanguage() = preferenceStore.getString("translation_target_language", "en")

    fun translationModel(provider: TranslationProvider) =
        preferenceStore.getString("translation_model_${provider.preferenceId}", "")

    fun translationModelChoiceType(provider: TranslationProvider) = preferenceStore.getEnum(
        "translation_model_choice_type_${provider.preferenceId}",
        TranslationModelChoiceType.AUTOMATIC,
    )
}

enum class TranslationProvider(
    val displayName: String,
    val preferenceId: String,
    val links: TranslationProviderLinks?,
) {
    NONE("None", "none", null),
    CLAUDE(
        "Claude",
        "claude",
        TranslationProviderLinks(
            key = "https://platform.claude.com/settings/keys",
            usage = "https://platform.claude.com/usage",
            billing = "https://platform.claude.com/settings/billing",
            revocation = "https://platform.claude.com/settings/keys",
        ),
    ),
    OPENAI(
        "OpenAI",
        "openai",
        TranslationProviderLinks(
            key = "https://platform.openai.com/api-keys",
            usage = "https://platform.openai.com/usage",
            billing = "https://platform.openai.com/settings/organization/billing/overview",
            revocation = "https://platform.openai.com/api-keys",
        ),
    ),
    OPENROUTER(
        "OpenRouter",
        "openrouter",
        TranslationProviderLinks(
            key = "https://openrouter.ai/settings/keys",
            usage = "https://openrouter.ai/activity",
            billing = "https://openrouter.ai/settings/credits",
            revocation = "https://openrouter.ai/settings/keys",
        ),
    ),
    GOOGLE(
        "Google Gemini",
        "google",
        TranslationProviderLinks(
            key = "https://aistudio.google.com/app/apikey",
            usage = "https://aistudio.google.com/app/usage",
            billing = "https://ai.google.dev/gemini-api/docs/billing",
            revocation = "https://aistudio.google.com/app/apikey",
        ),
    ),
}

data class TranslationProviderLinks(
    val key: String,
    val usage: String,
    val billing: String,
    val revocation: String,
)
