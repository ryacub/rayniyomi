package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider

object TranslationModelResolver {

    fun resolve(
        provider: TranslationProvider,
        choice: TranslationModelChoice,
        models: List<TranslationModelEntry>,
    ): TranslationModelResolution = when (choice.type) {
        TranslationModelChoiceType.AUTOMATIC ->
            TranslationModelCatalogFilter
                .filterForAutomatic(models, provider)
                .firstOrNull()
                ?.let { TranslationModelResolution.Selected(it) }
                ?: TranslationModelResolution.Unavailable(
                    reason = "No compatible model is available.",
                    replacements = emptyList(),
                )

        TranslationModelChoiceType.PINNED -> {
            val visibleModels = TranslationModelCatalogFilter.filter(models, provider)
            val selected = visibleModels.firstOrNull { it.id == choice.modelId }
            if (selected != null) {
                TranslationModelResolution.Selected(selected)
            } else {
                TranslationModelResolution.Unavailable(
                    reason = "The selected model is no longer compatible.",
                    replacements = visibleModels,
                )
            }
        }
    }
}

sealed class TranslationModelResolution {
    data class Selected(val model: TranslationModelEntry) : TranslationModelResolution()

    data class Unavailable(
        val reason: String,
        val replacements: List<TranslationModelEntry>,
    ) : TranslationModelResolution()
}
