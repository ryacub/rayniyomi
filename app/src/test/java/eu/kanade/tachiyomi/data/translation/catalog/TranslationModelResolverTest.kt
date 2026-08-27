package eu.kanade.tachiyomi.data.translation.catalog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationModelResolverTest {

    private val first = model("first")
    private val second = model("second")

    @Test
    fun `automatic choice selects first compatible model`() {
        TranslationModelResolver.resolve(
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
            choice = TranslationModelChoice(TranslationModelChoiceType.AUTOMATIC),
            models = listOf(first, second),
        ) shouldBe TranslationModelResolution.Selected(first)
    }

    @Test
    fun `automatic choice is unavailable when list is empty`() {
        TranslationModelResolver.resolve(
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
            choice = TranslationModelChoice(TranslationModelChoiceType.AUTOMATIC),
            models = emptyList(),
        ) shouldBe TranslationModelResolution.Unavailable(
            reason = "No compatible model is available.",
            replacements = emptyList(),
        )
    }

    @Test
    fun `pinned model resolves by stable ID`() {
        TranslationModelResolver.resolve(
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
            choice = TranslationModelChoice(TranslationModelChoiceType.PINNED, "second"),
            models = listOf(first, second),
        ) shouldBe TranslationModelResolution.Selected(second)
    }

    @Test
    fun `missing pinned model offers compatible replacements`() {
        TranslationModelResolver.resolve(
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
            choice = TranslationModelChoice(TranslationModelChoiceType.PINNED, "gone"),
            models = listOf(first, second),
        ) shouldBe TranslationModelResolution.Unavailable(
            reason = "The selected model is no longer compatible.",
            replacements = listOf(first, second),
        )
    }

    @Test
    fun `pinned compatible model does not require automatic output capacity`() {
        val limited = model("limited").copy(
            capabilities = model("limited").capabilities.copy(maxOutputTokens = 1_024),
        )

        TranslationModelResolver.resolve(
            provider = eu.kanade.tachiyomi.data.translation.TranslationProvider.OPENROUTER,
            choice = TranslationModelChoice(TranslationModelChoiceType.PINNED, "limited"),
            models = listOf(limited),
        ) shouldBe TranslationModelResolution.Selected(limited)
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
            maxOutputTokens = 4_096,
            structuredJsonOutput = true,
        ),
        cost = TranslationModelCost.FREE,
        freeTierEligible = true,
        stability = TranslationModelStability.UNKNOWN,
        dataTerms = null,
    )
}
