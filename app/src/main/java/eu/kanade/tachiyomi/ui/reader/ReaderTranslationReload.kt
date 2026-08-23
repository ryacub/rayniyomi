package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter

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
