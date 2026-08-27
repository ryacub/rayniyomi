package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider

data class TranslationModelPickerState(
    val models: List<TranslationModelEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    companion object {
        fun fromResult(
            result: TranslationCatalogResult,
            provider: TranslationProvider,
        ): TranslationModelPickerState = when (result) {
            is TranslationCatalogResult.Success -> TranslationModelPickerState(
                models = TranslationModelCatalogFilter.filter(result.catalog.models, provider),
            )
            is TranslationCatalogResult.Failure -> TranslationModelPickerState(
                models = TranslationModelCatalogFilter.filter(result.cachedModels, provider),
                errorMessage = result.reason,
            )
        }
    }
}
