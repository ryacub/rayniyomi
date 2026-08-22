package eu.kanade.tachiyomi.data.translation.catalog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationModelResolverTest {

    private val first = model("first")
    private val second = model("second")

    @Test
    fun `automatic choice selects first compatible model`() {
        TranslationModelResolver.resolve(
            TranslationModelChoice(TranslationModelChoiceType.AUTOMATIC),
            listOf(first, second),
        ) shouldBe TranslationModelResolution.Selected(first)
    }

    @Test
    fun `automatic choice is unavailable when list is empty`() {
        TranslationModelResolver.resolve(
            TranslationModelChoice(TranslationModelChoiceType.AUTOMATIC),
            emptyList(),
        ) shouldBe TranslationModelResolution.Unavailable(
            reason = "No compatible free model is available.",
            replacements = emptyList(),
        )
    }

    @Test
    fun `pinned model resolves by stable ID`() {
        TranslationModelResolver.resolve(
            TranslationModelChoice(TranslationModelChoiceType.PINNED, "second"),
            listOf(first, second),
        ) shouldBe TranslationModelResolution.Selected(second)
    }

    @Test
    fun `missing pinned model offers compatible replacements`() {
        TranslationModelResolver.resolve(
            TranslationModelChoice(TranslationModelChoiceType.PINNED, "gone"),
            listOf(first, second),
        ) shouldBe TranslationModelResolution.Unavailable(
            reason = "The selected model is no longer compatible.",
            replacements = listOf(first, second),
        )
    }

    private fun model(id: String) = TranslationModelEntry(
        id = id,
        displayName = id,
        capabilities = TranslationModelCapabilities(
            imageInput = true,
            textOutput = true,
            multilingualOcrAndTranslation = true,
            spatialBounds = true,
            normalizedCoordinates = true,
            originalAndTranslatedFields = true,
            minimumOutputTokens = 4_096,
            structuredJsonOutput = true,
        ),
        cost = TranslationModelCost.FREE,
        freeTierEligible = true,
        stability = TranslationModelStability.UNKNOWN,
        dataTerms = null,
    )
}
