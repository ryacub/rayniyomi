package eu.kanade.tachiyomi.data.translation.catalog

object TranslationModelPricingFormatter {

    fun format(model: TranslationModelEntry): String = buildList {
        model.pricing["prompt"]?.let { add("prompt $it USD/token") }
        model.pricing["completion"]?.let { add("completion $it USD/token") }
        model.pricing["image"]?.let { add("image $it USD/image") }
        model.pricing["request"]?.let { add("request $it USD/request") }
    }.ifEmpty {
        listOf(if (model.cost == TranslationModelCost.UNKNOWN) "pricing unknown" else "pricing unavailable")
    }.joinToString(", ")
}
