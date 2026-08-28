package eu.kanade.presentation.more.settings.screen.translation

import androidx.annotation.VisibleForTesting
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCapabilities
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCost
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelStability

/**
 * Representative model catalogs shared by previews, the JVM row-summary test,
 * and the instrumented Compose test so every consumer describes the same data.
 */
@VisibleForTesting
internal object TranslationModelPickerFixtures {

    fun claudeModels(): List<TranslationModelEntry> = listOf(
        model(
            id = "claude-sonnet-4-5",
            displayName = "Claude Sonnet 4.5",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = 8_192,
            pricing = mapOf("prompt" to "3", "completion" to "15"),
            dataTerms = "Anthropic does not train on commercial API traffic.",
        ),
        model(
            id = "claude-haiku-4-5",
            displayName = "Claude Haiku 4.5",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = 8_192,
            pricing = mapOf("prompt" to "1", "completion" to "5"),
        ),
    )

    fun openAiModels(): List<TranslationModelEntry> = listOf(
        model(
            id = "gpt-4o",
            displayName = "GPT-4o",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = 8_192,
            pricing = mapOf("prompt" to "2.5", "completion" to "10"),
        ),
        model(
            id = "gpt-4o-mini",
            displayName = "GPT-4o mini",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = null,
            pricing = mapOf("prompt" to "0.15", "completion" to "0.6"),
        ),
    )

    fun geminiModels(): List<TranslationModelEntry> = listOf(
        model(
            id = "gemini-2-5-flash",
            displayName = "Gemini 2.5 Flash",
            cost = TranslationModelCost.FREE,
            freeTierEligible = true,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = 8_192,
        ),
        model(
            id = "gemini-2-5-pro",
            displayName = "Gemini 2.5 Pro",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = 8_192,
            pricing = mapOf("prompt" to "1.25", "completion" to "10"),
        ),
    )

    fun openRouterModels(): List<TranslationModelEntry> = listOf(
        model(
            id = "meta-llama/llama-3.3-70b-instruct",
            displayName = "Llama 3.3 70B Instruct",
            cost = TranslationModelCost.FREE,
            freeTierEligible = true,
            stability = TranslationModelStability.UNKNOWN,
            maxOutputTokens = 8_192,
        ),
        model(
            id = "openai/gpt-4o",
            displayName = "OpenAI GPT-4o",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.STABLE,
            maxOutputTokens = 12_288,
            pricing = mapOf("prompt" to "2.5", "completion" to "10"),
        ),
        model(
            id = "anthropic/claude-sonnet-4.5",
            displayName = "Claude Sonnet 4.5",
            cost = TranslationModelCost.PAID,
            stability = TranslationModelStability.UNKNOWN,
            maxOutputTokens = 8_192,
            pricing = mapOf("prompt" to "3", "completion" to "15"),
            dataTerms = "Provider may retain inputs.",
        ),
    )

    fun longNameModel(): TranslationModelEntry = model(
        id = "long-named-model",
        displayName = "A most extraordinarily long human-readable model display name designed to make " +
            "the primary label wrap onto several lines instead of being clipped at a fixed row height",
        cost = TranslationModelCost.PAID,
        stability = TranslationModelStability.STABLE,
        maxOutputTokens = 8_192,
    )

    fun forProvider(provider: TranslationProvider): List<TranslationModelEntry> = when (provider) {
        TranslationProvider.CLAUDE -> claudeModels()
        TranslationProvider.OPENAI -> openAiModels()
        TranslationProvider.GOOGLE -> geminiModels()
        TranslationProvider.OPENROUTER -> openRouterModels()
        TranslationProvider.NONE -> emptyList()
    }

    private fun model(
        id: String,
        displayName: String,
        cost: TranslationModelCost,
        stability: TranslationModelStability,
        maxOutputTokens: Int?,
        freeTierEligible: Boolean? = null,
        pricing: Map<String, String> = emptyMap(),
        dataTerms: String? = null,
    ) = TranslationModelEntry(
        id = id,
        displayName = displayName,
        capabilities = TranslationModelCapabilities(
            imageInput = true,
            textOutput = true,
            multilingualOcrAndTranslation = true,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = maxOutputTokens,
            structuredJsonOutput = false,
            inputModalities = listOf("image"),
            outputModalities = listOf("text"),
        ),
        cost = cost,
        freeTierEligible = freeTierEligible,
        stability = stability,
        dataTerms = dataTerms,
        pricing = pricing,
    )
}
