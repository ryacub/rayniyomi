package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

fun List<ChapterList.Item>.resumeChapter(manga: Manga): Chapter? {
    val next = getNextUnread(manga) ?: return null
    return next.takeIf { it.lastPageRead > 0L || any { item -> item.chapter.read } }
}
