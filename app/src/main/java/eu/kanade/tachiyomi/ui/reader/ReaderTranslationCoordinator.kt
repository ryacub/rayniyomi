package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.toggle
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Owns the translated-pages reaction for the reader: it watches [TranslationManager.languageGeneration],
 * recomputes whether the current chapter has a translation, decides when the viewer must reload,
 * and toggles between translated and original pages.
 *
 * The ViewModel supplies the open viewer chapters through one accessor, one reducer that writes
 * the translation state through [updateTranslation], and event delivery through callbacks, so
 * state ownership stays in one place. The shown-translated-pages preference is read through
 * [readerPreferences]: the coordinator is its only writer.
 */
class ReaderTranslationCoordinator(
    private val translationManager: TranslationManager,
    private val readerPreferences: ReaderPreferences,
    private val scope: CoroutineScope,
    private val getViewerChapters: () -> ViewerChapters?,
    private val hasTranslationFor: (Chapter) -> Boolean,
    private val chapterIdFlow: Flow<Long?>,
    private val updateTranslation: ((ReaderTranslationUiState) -> ReaderTranslationUiState) -> Unit,
    private val onReload: suspend () -> Unit,
    private val reloadChapter: suspend (ReaderChapter) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private var toggleJob: Job? = null

    /**
     * Starts watching language generation changes and the open chapter's translation state.
     * Call once from the owner's init block; collection runs on the supplied scope.
     * The language-generation collector skips the initial emission.
     */
    fun start() {
        scope.launch(ioDispatcher) {
            translationManager.languageGeneration
                .drop(1)
                .collect {
                    val currChapter = getViewerChapters()?.currChapter ?: return@collect
                    updateTranslation { it.copy(hasTranslation = hasTranslationFor(currChapter.chapter)) }
                    if (readerPreferences.showTranslatedPages().get() && reloadOrFalse(currChapter, reloadChapter)) {
                        onReload()
                    }
                }
        }
        scope.launch(ioDispatcher) {
            translationStateFlow(translationManager.translationStates, chapterIdFlow)
                .collect { state -> updateTranslation { it.copy(translationState = state) } }
        }
    }

    /**
     * Toggles between showing translated and original pages. The function reloads the
     * current chapter's pages. It also reloads the loaded and loading adjacent chapters, so they
     * show pages that match the new preference before the user swipes. A failed current-chapter
     * reload reverts the toggle so the icon, the preference, and the displayed pages agree.
     */
    fun toggleTranslatedPages() {
        val newValue = readerPreferences.showTranslatedPages().toggle()
        updateTranslation { it.copy(showTranslatedPages = newValue) }
        toggleJob?.cancel()
        toggleJob = scope.launch {
            val viewerChapters = getViewerChapters() ?: return@launch
            val installed = reloadViewerChaptersForTranslationToggle(viewerChapters, reloadChapter)
            if (!installed) {
                readerPreferences.showTranslatedPages().set(!newValue)
                updateTranslation { it.copy(showTranslatedPages = !newValue) }
                return@launch
            }
            onReload()
        }
    }
}
