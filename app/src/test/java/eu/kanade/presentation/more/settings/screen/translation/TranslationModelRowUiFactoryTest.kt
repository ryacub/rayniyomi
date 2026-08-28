package eu.kanade.presentation.more.settings.screen.translation

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCapabilities
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCost
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelStability
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class TranslationModelRowUiFactoryTest {

    @Test
    fun `title falls back to id when display name is blank`() {
        val row = TranslationModelRowUiFactory.create(model(id = "m1", displayName = "  "))

        row.title shouldBe "m1"
    }

    @Test
    fun `paid stable model shows cost and token limit`() {
        val row = TranslationModelRowUiFactory.create(
            model(
                id = "m1",
                displayName = "Model 1",
                cost = TranslationModelCost.PAID,
                stability = TranslationModelStability.STABLE,
                maxOutputTokens = 8_192,
            ),
        )

        row.summary shouldBe listOf(
            TranslationModelRowUi.SummaryToken.Paid,
            TranslationModelRowUi.SummaryToken.MaxOutputTokens(8_192),
        )
    }

    @Test
    fun `unknown stability replaces token limit and stays within cap`() {
        val row = TranslationModelRowUiFactory.create(
            model(
                id = "m1",
                displayName = "Model 1",
                cost = TranslationModelCost.PAID,
                stability = TranslationModelStability.UNKNOWN,
                maxOutputTokens = 8_192,
            ),
        )

        row.summary shouldBe listOf(
            TranslationModelRowUi.SummaryToken.Paid,
            TranslationModelRowUi.SummaryToken.StabilityUnknown,
        )
        row.summary.size shouldBe TranslationModelRowUiFactory.MAX_SUMMARY_TOKENS
    }

    @Test
    fun `free model shows free cost token`() {
        val row = TranslationModelRowUiFactory.create(
            model(
                id = "m1",
                displayName = "Model 1",
                cost = TranslationModelCost.FREE,
                stability = TranslationModelStability.STABLE,
                maxOutputTokens = null,
            ),
        )

        row.summary.first() shouldBe TranslationModelRowUi.SummaryToken.Free
    }

    @ParameterizedTest
    @EnumSource(value = TranslationProvider::class, names = ["CLAUDE", "OPENAI", "GOOGLE", "OPENROUTER"])
    fun `summary never exceeds max summary tokens`(provider: TranslationProvider) {
        TranslationModelPickerFixtures.forProvider(provider).forEach { model ->
            val row = TranslationModelRowUiFactory.create(model)
            (row.summary.size <= TranslationModelRowUiFactory.MAX_SUMMARY_TOKENS) shouldBe true
        }
    }

    @Test
    fun `details always start with model id then pricing`() {
        val row = TranslationModelRowUiFactory.create(
            TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE).first(),
        )

        row.details.first() shouldBe TranslationModelRowUi.DetailToken.ModelId("claude-sonnet-4-5")
        row.details[1] shouldBe TranslationModelRowUi.DetailToken.Pricing(
            "prompt 3 USD/token, completion 15 USD/token",
        )
    }

    @Test
    fun `details omit empty modalities and blank data terms`() {
        val row = TranslationModelRowUiFactory.create(
            model(
                id = "m1",
                displayName = "Model 1",
                cost = TranslationModelCost.PAID,
                stability = TranslationModelStability.STABLE,
                maxOutputTokens = 8_192,
                inputModalities = emptyList(),
                outputModalities = emptyList(),
                dataTerms = null,
            ),
        )

        row.details shouldBe listOf(
            TranslationModelRowUi.DetailToken.ModelId("m1"),
            TranslationModelRowUi.DetailToken.Pricing("pricing unavailable"),
        )
    }

    @ParameterizedTest
    @EnumSource(value = TranslationProvider::class, names = ["CLAUDE", "OPENAI", "GOOGLE", "OPENROUTER"])
    fun `create does not throw for any fixture`(provider: TranslationProvider) {
        TranslationModelPickerFixtures.forProvider(provider).forEach { model ->
            val row = TranslationModelRowUiFactory.create(model)
            row.title.isNotBlank() shouldBe true
        }
    }

    private fun model(
        id: String,
        displayName: String,
        cost: TranslationModelCost = TranslationModelCost.PAID,
        stability: TranslationModelStability = TranslationModelStability.STABLE,
        maxOutputTokens: Int? = null,
        inputModalities: List<String> = listOf("image"),
        outputModalities: List<String> = listOf("text"),
        dataTerms: String? = null,
    ) = TranslationModelEntry(
        id = id,
        displayName = displayName,
        capabilities = TranslationModelCapabilities(
            imageInput = true,
            textOutput = true,
            multilingualOcrAndTranslation = false,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = maxOutputTokens,
            structuredJsonOutput = false,
            inputModalities = inputModalities,
            outputModalities = outputModalities,
        ),
        cost = cost,
        freeTierEligible = null,
        stability = stability,
        dataTerms = dataTerms,
    )
}
