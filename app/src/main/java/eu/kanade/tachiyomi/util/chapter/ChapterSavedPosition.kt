package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

data class SavedReadingPosition(val chapter: Chapter, val page: Int?)

fun List<ChapterList.Item>.savedReadingPosition(manga: Manga): SavedReadingPosition? {
    val next = getNextUnread(manga) ?: return null
    return when {
        next.lastPageRead > 0L -> SavedReadingPosition(next, page = next.lastPageRead.toInt() + 1)
        any { it.chapter.read } -> SavedReadingPosition(next, page = null)
        else -> null
    }
}
