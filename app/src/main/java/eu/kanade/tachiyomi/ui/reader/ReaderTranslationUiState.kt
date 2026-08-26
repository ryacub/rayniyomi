package eu.kanade.tachiyomi.ui.reader

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.translation.TranslationState
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters

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

/**
 * A snapshot of the reader state [ReaderTranslationCoordinator] reads. One accessor returning this
 * replaces the four per-field getter lambdas the coordinator used to take; each call site takes a
 * fresh snapshot, so the read timing matches the per-field reads it replaces.
 */
data class ReaderTranslationContext(
    val viewerChapters: ViewerChapters?,
    val showTranslatedPages: Boolean,
) {
    val currChapter: ReaderChapter? get() = viewerChapters?.currChapter
}
