package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.coroutines.CancellationException

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
 *   Event.ReloadViewerChapters; false when nothing changed or a handled failure
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
