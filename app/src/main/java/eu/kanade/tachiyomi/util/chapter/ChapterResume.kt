package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

fun List<ChapterList.Item>.resumeChapter(manga: Manga): Chapter? {
    val next = getNextUnread(manga) ?: return null
    return next.takeIf { it.lastPageRead > 0L || any { item -> item.chapter.read } }
}

sealed interface StartAction {
    data class FirstStart(val chapter: Chapter) : StartAction
    data class Resume(val chapter: Chapter) : StartAction
    data object None : StartAction
}

fun List<ChapterList.Item>.startAction(manga: Manga): StartAction {
    val next = getNextUnread(manga) ?: return StartAction.None
    val touched = any { it.chapter.read || it.chapter.lastPageRead > 0L }
    return if (touched) StartAction.Resume(next) else StartAction.FirstStart(next)
}
