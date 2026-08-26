package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.Chapter
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
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.toggle

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
    private val cancelAdjacentPreload: suspend () -> Unit = {},
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
                    if (prepareTranslationReload(currChapter, readerPreferences.showTranslatedPages().get())) {
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
     * Toggles between showing translated and original pages. The function rebuilds the
     * current chapter's page list through its loader. It also rebuilds the loaded adjacent
     * chapters, so they show pages that match the new preference before the user swipes.
     * A failed current-chapter rebuild reverts the toggle so the icon, the preference, and
     * the displayed pages agree. It first cancels any in-flight adjacent-chapter preload,
     * because that preload writes the same [ReaderChapter.state] this rebuild writes.
     */
    fun toggleTranslatedPages() {
        val newValue = readerPreferences.showTranslatedPages().toggle()
        updateTranslation { it.copy(showTranslatedPages = newValue) }
        toggleJob?.cancel()
        toggleJob = scope.launch {
            // A preload started by the viewer writes the same ReaderChapter.state this rebuild
            // writes. Wait for it to finish cancelling, so it cannot land old-language pages on
            // an adjacent chapter afterwards.
            cancelAdjacentPreload()
            val viewerChapters = getViewerChapters() ?: return@launch
            val installed = withContext(ioDispatcher) {
                reloadViewerChaptersForTranslationToggle(viewerChapters)
            }
            if (!installed) {
                readerPreferences.showTranslatedPages().set(!newValue)
                updateTranslation { it.copy(showTranslatedPages = !newValue) }
                return@launch
            }
            onReload()
        }
    }
}
