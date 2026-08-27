package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider

object TranslationModelCatalogFilter {

    fun filter(models: List<TranslationModelEntry>): List<TranslationModelEntry> =
        filter(models, TranslationProvider.OPENROUTER)

    fun filter(
        models: List<TranslationModelEntry>,
        provider: TranslationProvider,
    ): List<TranslationModelEntry> = models.filter { it.capabilities.supportsImageTranslation() }

    fun filterForAutomatic(
        models: List<TranslationModelEntry>,
        provider: TranslationProvider,
    ): List<TranslationModelEntry> = when (provider) {
        TranslationProvider.OPENROUTER -> models.filter {
            it.capabilities.supportsAutomaticTranslation()
        }
        else -> filter(models, provider)
    }
}
