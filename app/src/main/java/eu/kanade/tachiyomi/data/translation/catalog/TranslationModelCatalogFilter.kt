package eu.kanade.tachiyomi.data.translation.catalog

object TranslationModelCatalogFilter {

    fun filter(models: List<TranslationModelEntry>): List<TranslationModelEntry> = models.filter { model ->
        model.capabilities.supportsTranslationRequirements() && model.cost == TranslationModelCost.FREE
    }
}
