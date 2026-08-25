package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.Chapter
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationState
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager

/**
 * Owns the translated-pages reaction for the reader: it watches [TranslationManager.languageGeneration],
 * recomputes whether the current chapter has a translation, decides when the viewer must reload,
 * and toggles between translated and original pages.
 *
 * The ViewModel supplies state accessors and event delivery through callbacks so state ownership
 * stays in one place.
 */
class ReaderTranslationCoordinator(
    private val translationStorageManager: TranslationStorageManager,
    private val translationPreferences: TranslationPreferences,
    private val translationManager: TranslationManager,
    private val sourceManager: MangaSourceManager,
    private val readerPreferences: ReaderPreferences,
    private val scope: CoroutineScope,
    private val currentManga: () -> Manga?,
    private val getCurrChapter: () -> ReaderChapter?,
    private val getViewerChapters: () -> ViewerChapters?,
    private val getShowTranslatedPages: () -> Boolean,
    private val chapterIdFlow: Flow<Long?>,
    private val onHasTranslationChange: (hasTranslation: Boolean) -> Unit,
    private val onTranslationStateChange: (TranslationState) -> Unit,
    private val onShowTranslatedPagesChange: (showTranslatedPages: Boolean) -> Unit,
    private val onReload: suspend () -> Unit,
) {

    private var toggleJob: Job? = null

    /**
     * Starts watching language generation changes and the open chapter's translation state.
     * Call once from the owner's init block; collection runs on the supplied scope.
     * The language-generation collector skips the initial emission.
     */
    fun start() {
        scope.launchIO {
            translationManager.languageGeneration
                .drop(1)
                .collect {
                    val currChapter = getCurrChapter() ?: return@collect
                    onHasTranslationChange(computeHasTranslation(currChapter.chapter))
                    if (prepareTranslationReload(currChapter, getShowTranslatedPages())) {
                        onReload()
                    }
                }
        }
        scope.launchIO {
            translationStateFlow(translationManager.translationStates, chapterIdFlow)
                .collect(onTranslationStateChange)
        }
    }

    fun computeHasTranslation(chapter: Chapter): Boolean {
        val manga = currentManga() ?: return false
        val currentSource = sourceManager.getOrStub(manga.source)
        return translationStorageManager.isChapterTranslated(
            chapter.name,
            chapter.scanlator,
            manga.title,
            currentSource,
            translationPreferences.targetLanguage().get(),
        )
    }

    /**
     * Toggles between showing translated and original pages. The function rebuilds the
     * current chapter's page list through its loader. It also rebuilds the loaded adjacent
     * chapters, so they show pages that match the new preference before the user swipes.
     * A failed current-chapter rebuild reverts the toggle so the icon, the preference, and
     * the displayed pages agree.
     */
    fun toggleTranslatedPages() {
        val newValue = readerPreferences.showTranslatedPages().toggle()
        onShowTranslatedPagesChange(newValue)
        toggleJob?.cancel()
        toggleJob = scope.launchIO {
            val currChapter = getCurrChapter() ?: return@launchIO
            val viewerChapters = getViewerChapters()
            val installed = try {
                reloadViewerChaptersForTranslationToggle(
                    currChapter = currChapter,
                    prevChapter = viewerChapters?.prevChapter,
                    nextChapter = viewerChapters?.nextChapter,
                )
            } catch (e: CancellationException) {
                throw e
            }
            if (!installed) {
                readerPreferences.showTranslatedPages().set(!newValue)
                onShowTranslatedPagesChange(!newValue)
                return@launchIO
            }
            onReload()
        }
    }
}
