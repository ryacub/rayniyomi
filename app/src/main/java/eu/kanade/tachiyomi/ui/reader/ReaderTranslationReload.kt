package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.Chapter
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager

/**
 * Prepares [chapter] for a reload after the target language changed, keeping the read position.
 *
 * A chapter left in [ReaderChapter.State.Wait] makes ChapterLoader rebuild its page list; a
 * Loaded chapter would keep serving pages in the previous language.
 *
 * @return whether the caller must trigger a viewer reload.
 */
internal fun prepareTranslationReload(
    chapter: ReaderChapter,
    showTranslatedPages: Boolean,
): Boolean {
    if (!showTranslatedPages) return false
    chapter.requestedPage = chapter.chapter.last_page_read
    chapter.state = ReaderChapter.State.Wait
    return true
}

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
 * Rebuilds [chapter]'s page list through its existing page loader after the
 * translated-pages toggle flipped. Re-invoking getPages() makes loaders such as
 * DownloadPageLoader re-read showTranslatedPages() and re-resolve each page URI,
 * which the ReloadViewerChapters event alone does not do: that event only
 * re-attaches the previously built list.
 *
 * Guards precede all mutation. A Loading chapter is skipped: the in-flight
 * ChapterLoader load reads the new preference itself. Wait and Error chapters are
 * rebuilt like Loaded ones. The Loaded state is installed atomically at the end;
 * no intermediate state is written.
 *
 * @return true when a fresh Loaded list was installed and the caller must send
 *   ReaderEvent.ReloadViewerChapters; false when nothing changed or a handled failure
 *   left State.Error installed. Only CancellationException propagates.
 */
internal suspend fun reloadChapterPagesForTranslationToggle(chapter: ReaderChapter): Boolean {
    val loader = chapter.pageLoader ?: return false
    if (chapter.state is ReaderChapter.State.Loading) return false
    return try {
        val pages = loader.getPages().onEach { it.chapter = chapter }
        if (pages.isEmpty()) {
            chapter.state = ReaderChapter.State.Error(Exception("Rebuilt page list is empty"))
            false
        } else {
            chapter.state = ReaderChapter.State.Loaded(pages)
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        chapter.state = ReaderChapter.State.Error(e)
        false
    }
}

/**
 * Rebuilds the page lists of the visible chapters of [chapters] after the translated-pages
 * toggle flipped.
 *
 * The function rebuilds [ViewerChapters.currChapter] first. If that rebuild fails, the function
 * returns false and leaves the adjacent chapters alone, because the caller reverts the toggle.
 *
 * If the current rebuild succeeds, the function rebuilds each adjacent chapter that is in
 * [ReaderChapter.State.Loaded]. ChapterLoader rebuilds a Wait or an Error chapter on
 * navigation, so those need no work here. The function leaves a Loading chapter to its
 * in-flight load.
 *
 * An adjacent rebuild that fails leaves State.Error on that chapter only. It does not change the
 * return value, because the current chapter is correct and the toggle must stand.
 *
 * @return true when the current chapter got a fresh page list and the caller must send
 *   ReaderEvent.ReloadViewerChapters.
 */
internal suspend fun reloadViewerChaptersForTranslationToggle(chapters: ViewerChapters): Boolean {
    if (!reloadChapterPagesForTranslationToggle(chapters.currChapter)) return false
    listOfNotNull(chapters.prevChapter, chapters.nextChapter)
        .filter { it.state is ReaderChapter.State.Loaded }
        .forEach { reloadChapterPagesForTranslationToggle(it) }
    return true
}
