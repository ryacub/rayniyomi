package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.coroutines.CancellationException

/**
 * Refreshes the model catalog in the background and resolves an AUTOMATIC
 * choice, so a persisted model id exists before translation or picker use.
 */
object TranslationCatalogPrefetch {

    suspend fun refreshAndResolveAutomatic(
        repository: TranslationModelCatalogRepository,
        provider: TranslationProvider,
        apiKey: String,
        choiceType: TranslationModelChoiceType,
        setModelId: (String) -> Unit,
    ) {
        if (provider == TranslationProvider.NONE || apiKey.isBlank()) return
        try {
            val result = repository.load(provider, apiKey, forceRefresh = true)
            if (result !is TranslationCatalogResult.Success) return
            if (choiceType != TranslationModelChoiceType.AUTOMATIC) return
            when (
                val resolution =
                    TranslationModelResolver.resolve(
                        provider = provider,
                        choice = TranslationModelChoice(choiceType),
                        models = result.catalog.models,
                    )
            ) {
                is TranslationModelResolution.Selected -> setModelId(resolution.model.id)
                is TranslationModelResolution.Unavailable -> setModelId("")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The picker surfaces catalog errors when the user opens it.
        }
    }
}
