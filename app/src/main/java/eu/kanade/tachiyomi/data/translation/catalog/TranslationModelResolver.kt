package eu.kanade.tachiyomi.data.translation.catalog

object TranslationModelResolver {

    fun resolve(
        choice: TranslationModelChoice,
        compatibleModels: List<TranslationModelEntry>,
    ): TranslationModelResolution = when (choice.type) {
        TranslationModelChoiceType.AUTOMATIC -> compatibleModels.firstOrNull()
            ?.let { TranslationModelResolution.Selected(it) }
            ?: TranslationModelResolution.Unavailable(
                reason = "No compatible free model is available.",
                replacements = emptyList(),
            )

        TranslationModelChoiceType.PINNED -> {
            val selected = compatibleModels.firstOrNull { it.id == choice.modelId }
            if (selected != null) {
                TranslationModelResolution.Selected(selected)
            } else {
                TranslationModelResolution.Unavailable(
                    reason = "The selected model is no longer compatible.",
                    replacements = compatibleModels,
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
