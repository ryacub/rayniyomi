package eu.kanade.presentation.more.settings.screen.translation

import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCost
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelPricingFormatter
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelStability

/** A localization-free description of one picker row. The composable turns these tokens into strings. */
data class TranslationModelRowUi(
    val id: String,
    val title: String,
    val summary: List<SummaryToken>,
    val details: List<DetailToken>,
) {
    sealed interface SummaryToken {
        data object Free : SummaryToken
        data object Paid : SummaryToken
        data object CostUnknown : SummaryToken
        data object Stable : SummaryToken
        data object StabilityUnknown : SummaryToken
        data class MaxOutputTokens(val tokens: Int) : SummaryToken
    }

    sealed interface DetailToken {
        data class ModelId(val id: String) : DetailToken
        data class Pricing(val text: String) : DetailToken
        data class InputModalities(val values: String) : DetailToken
        data class OutputModalities(val values: String) : DetailToken
        data class DataTerms(val text: String) : DetailToken
    }
}

object TranslationModelRowUiFactory {

    /** Summary is capped at [MAX_SUMMARY_TOKENS] so the second line stays one short line. */
    const val MAX_SUMMARY_TOKENS = 2

    fun create(model: TranslationModelEntry): TranslationModelRowUi {
        val costToken = when (model.cost) {
            TranslationModelCost.FREE -> TranslationModelRowUi.SummaryToken.Free
            TranslationModelCost.PAID -> TranslationModelRowUi.SummaryToken.Paid
            TranslationModelCost.UNKNOWN -> TranslationModelRowUi.SummaryToken.CostUnknown
        }
        val summary = buildList {
            add(costToken)
            if (model.stability == TranslationModelStability.UNKNOWN) {
                add(TranslationModelRowUi.SummaryToken.StabilityUnknown)
            }
            model.capabilities.maxOutputTokens?.let {
                add(TranslationModelRowUi.SummaryToken.MaxOutputTokens(it))
            }
        }.take(MAX_SUMMARY_TOKENS)

        val details = buildList {
            add(TranslationModelRowUi.DetailToken.ModelId(model.id))
            add(TranslationModelRowUi.DetailToken.Pricing(TranslationModelPricingFormatter.format(model)))
            model.capabilities.inputModalities.takeIf { it.isNotEmpty() }?.let {
                add(TranslationModelRowUi.DetailToken.InputModalities(it.joinToString(", ")))
            }
            model.capabilities.outputModalities.takeIf { it.isNotEmpty() }?.let {
                add(TranslationModelRowUi.DetailToken.OutputModalities(it.joinToString(", ")))
            }
            model.dataTerms?.takeIf { it.isNotBlank() }?.let {
                add(TranslationModelRowUi.DetailToken.DataTerms(it))
            }
        }

        return TranslationModelRowUi(
            id = model.id,
            title = model.displayName.takeIf { it.isNotBlank() } ?: model.id,
            summary = summary,
            details = details,
        )
    }
}
