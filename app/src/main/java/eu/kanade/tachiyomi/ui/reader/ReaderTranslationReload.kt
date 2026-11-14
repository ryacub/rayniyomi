package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import logcat.asLog
import logcat.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager

/**
 * Whether [chapter] has a stored translation in [targetLanguage] for [manga].
 *
 * Free function so both callers — the ViewModel's chapter load and the coordinator's
 * language-generation reaction — share one implementation, and so it is unit-testable without
 * building a coordinator. A null [manga] means no chapter is open, which is not translated.
 */
internal fun computeHasTranslation(
    manga: Manga?,
    chapter: Chapter,
    sourceManager: MangaSourceManager,
    translationStorageManager: TranslationStorageManager,
    targetLanguage: String,
): Boolean {
    val currentManga = manga ?: return false
    return translationStorageManager.isChapterTranslated(
        chapter.name,
        chapter.scanlator,
        currentManga.title,
        sourceManager.getOrStub(currentManga.source),
        targetLanguage,
    )
}

/**
 * Loads the pages of the visible chapters of [chapters] again after the translated-pages toggle
 * flipped. Only DownloadPageLoader reads the preference, and it reads it on each page-list load.
 *
 * The function reloads [ViewerChapters.currChapter] first. If that reload fails, the function
 * returns false and leaves the adjacent chapters alone, because the caller reverts the toggle.
 *
 * If the current reload succeeds, the function reloads each adjacent chapter that is loaded or
 * loading. A loading chapter restarts, so it reads the new preference. A Wait or an Error chapter
 * loads on navigation, so it needs no work here. An adjacent reload that fails keeps that
 * chapter's old pages and does not change the return value.
 *
 * @return true when the current chapter got a fresh page list and the caller must send
 *   ReaderEvent.ReloadViewerChapters.
 */
internal suspend fun reloadViewerChaptersForTranslationToggle(
    chapters: ViewerChapters,
    reloadChapter: suspend (ReaderChapter) -> Unit,
): Boolean {
    if (!reloadOrFalse(chapters.currChapter, reloadChapter)) return false
    listOfNotNull(chapters.prevChapter, chapters.nextChapter)
        .filter { it.state is ReaderChapter.State.Loaded || it.state == ReaderChapter.State.Loading }
        .forEach { reloadOrFalse(it, reloadChapter) }
    return true
}

/**
 * Reloads [chapter] and returns false, with a log line, when the reload fails or the chapter is
 * released during its reload. Any other cancellation propagates.
 */
internal suspend fun reloadOrFalse(chapter: ReaderChapter, reloadChapter: suspend (ReaderChapter) -> Unit): Boolean =
    try {
        reloadChapter(chapter)
        true
    } catch (e: ChapterLoader.ChapterReleasedException) {
        logReloadFailure(chapter, e)
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logReloadFailure(chapter, e)
        false
    }

private fun logReloadFailure(chapter: ReaderChapter, error: Throwable) {
    logcat("ReaderTranslationReload", LogPriority.ERROR) {
        "Could not reload ${chapter.chapter.name}\n${error.asLog()}"
    }
}
