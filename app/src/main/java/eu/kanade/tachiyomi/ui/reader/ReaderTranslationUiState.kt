package eu.kanade.tachiyomi.ui.reader

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.translation.TranslationState

/**
 * The reader's translation-related UI state, grouped so the three fields move as one value.
 *
 * @property showTranslatedPages whether the viewer currently shows translated pages.
 * @property hasTranslation whether the open chapter has a stored translation for the target language.
 * @property translationState the in-flight translation progress for the open chapter.
 */
@Immutable
data class ReaderTranslationUiState(
    val showTranslatedPages: Boolean = false,
    val hasTranslation: Boolean = false,
    val translationState: TranslationState = TranslationState.Idle,
)
