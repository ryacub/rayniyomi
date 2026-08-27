package eu.kanade.tachiyomi.data.translation.catalog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationModelPickerStateTest {

    @Test
    fun `success exposes only visible OpenRouter models`() {
        val state = TranslationModelPickerState.fromResult(
            result = TranslationCatalogResult.Success(
                catalog = catalog(
                    model("vision"),
                    model("text-only", imageInput = false),
                ),
                fromCache = false,
            ),
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
        )

        state.models.map { it.id } shouldBe listOf("vision")
        state.errorMessage shouldBe null
        state.isLoading shouldBe false
    }

    @Test
    fun `failure retains cached models and exposes refresh error`() {
        val state = TranslationModelPickerState.fromResult(
            result = TranslationCatalogResult.Failure(
                reason = "refresh failed",
                cachedModels = listOf(model("cached")),
            ),
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
        )

        state.models.map { it.id } shouldBe listOf("cached")
        state.errorMessage shouldBe "refresh failed"
        state.isLoading shouldBe false
    }

    private fun catalog(vararg models: TranslationModelEntry) = TranslationModelCatalog(
        provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
        fetchedAtEpochMilliseconds = 1_000,
        models = models.toList(),
    )

    private fun model(id: String, imageInput: Boolean = true) = TranslationModelEntry(
        id = id,
        displayName = id,
        capabilities = TranslationModelCapabilities(
            imageInput = imageInput,
            textOutput = true,
            multilingualOcrAndTranslation = false,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = 4_096,
            structuredJsonOutput = false,
        ),
        cost = TranslationModelCost.PAID,
        freeTierEligible = null,
        stability = TranslationModelStability.UNKNOWN,
        dataTerms = null,
    )
}
