package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider

object TranslationModelCatalogFilter {

    fun filter(models: List<TranslationModelEntry>): List<TranslationModelEntry> =
        filter(models, TranslationProvider.OPENROUTER)

    fun filter(
        models: List<TranslationModelEntry>,
        provider: TranslationProvider,
    ): List<TranslationModelEntry> = when (provider) {
        TranslationProvider.OPENROUTER -> models.filter { model ->
            model.capabilities.supportsTranslationRequirements() &&
                model.cost == TranslationModelCost.FREE
        }
        // Native APIs are paid, so the cost gate is waived there.
        else -> models.filter { model ->
            model.capabilities.imageInput && model.capabilities.textOutput
        }
    }
}
