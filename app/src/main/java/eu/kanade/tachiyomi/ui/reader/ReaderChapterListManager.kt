package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.items.chapter.model.toDbChapter
import eu.kanade.tachiyomi.data.database.models.manga.isRecognizedNumber
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.filterDownloadedChapters
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.service.getChapterSort

/**
 * Manages chapter list initialization, filtering, and query operations for ReaderViewModel.
 * Extracted to reduce complexity and improve separation of concerns.
 */
internal class ReaderChapterListManager(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val downloadManager: MangaDownloadManager,
    private val readerPreferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
) {
    private var _chapterList: List<ReaderChapter> = emptyList()
    val chapterList: List<ReaderChapter>
        get() = _chapterList

    var chapterId: Long = -1L

    /**
     * Initialize the chapter list for the given manga.
     * Retrieves chapters, applies filters (skip-read, skip-filtered, skip-dupe, downloaded-only),
     * sorts them, and converts to ReaderChapter objects.
     */
    suspend fun initChapterList(manga: Manga): List<ReaderChapter> {
        val chapters = fetchChapters(manga)
        val selectedChapter = findSelectedChapter(chapters)
        val filteredChapters = applyUserFilters(chapters, selectedChapter, manga)
        _chapterList = processChapterList(filteredChapters, selectedChapter, manga)
        return _chapterList
    }

    /**
     * Get a chapter by its ID.
     * @throws NoSuchElementException if chapter is not found.
     */
    fun getChapterById(chapterId: Long): ReaderChapter {
        return chapterList.first { chapterId == it.chapter.id }
    }

    /**
     * Get the adjacent chapters for navigation.
     * Returns AdjacentChapters with current, previous, and next chapters.
     * Previous/next may be null at list boundaries.
     */
    fun getAdjacentChapters(chapter: ReaderChapter): AdjacentChapters {
        val chapterPos = chapterList.indexOf(chapter)
        return AdjacentChapters(
            current = chapter,
            previous = chapterList.getOrNull(chapterPos - 1),
            next = chapterList.getOrNull(chapterPos + 1),
        )
    }

    /**
     * Get the chapter that should be deleted based on the current chapter position
     * and the removeAfterReadSlots preference.
     * Returns null if the chapter to delete is out of bounds.
     */
    fun getChapterForDeletion(currentChapter: ReaderChapter, removeAfterReadSlots: Int): ReaderChapter? {
        val currentChapterPosition = chapterList.indexOf(currentChapter)
        return chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)
    }

    /**
     * Get the list of ChapterUpdate objects for duplicate unread chapters
     * that should be marked as read when the given chapter is completed.
     * Only includes chapters with recognized numbers matching the completed chapter's number.
     */
    fun getDuplicateUnreadChapterUpdates(readerChapter: ReaderChapter): List<ChapterUpdate> {
        return chapterList
            .mapNotNull {
                val chapter = it.chapter
                if (
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapter_number == readerChapter.chapter.chapter_number
                ) {
                    ChapterUpdate(id = chapter.id!!, read = true)
                } else {
                    null
                }
            }
    }

    // Private helper methods

    /**
     * Fetches chapters asynchronously from the DB layer.
     *
     * This path is suspend-only because reader initialization runs from UI-driven flows in
     * [ReaderActivity] and [ReaderViewModel]. Keeping this as a suspend call avoids reintroducing
     * blocking bridges (for example, runBlocking) that previously caused startup/read-open ANR risk.
     */
    private suspend fun fetchChapters(manga: Manga): List<Chapter> {
        return getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true)
    }

    private fun findSelectedChapter(chapters: List<Chapter>): Chapter {
        return chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")
    }

    private fun applyUserFilters(
        chapters: List<Chapter>,
        selectedChapter: Chapter,
        manga: Manga,
    ): List<Chapter> {
        val shouldApplyFilters = readerPreferences.skipRead().get() || readerPreferences.skipFiltered().get()
        if (!shouldApplyFilters) {
            return chapters
        }

        val filteredChapters = chapters.filterNot { chapter ->
            shouldFilterChapter(chapter, manga)
        }

        // Always include selected chapter even if it would be filtered
        return if (filteredChapters.any { it.id == chapterId }) {
            filteredChapters
        } else {
            filteredChapters + listOf(selectedChapter)
        }
    }

    private fun shouldFilterChapter(chapter: Chapter, manga: Manga): Boolean {
        return when {
            shouldSkipByReadStatus(chapter) -> true
            shouldSkipByMangaFilters(chapter, manga) -> true
            else -> false
        }
    }

    private fun shouldSkipByReadStatus(chapter: Chapter): Boolean {
        return readerPreferences.skipRead().get() && chapter.read
    }

    private fun shouldSkipByMangaFilters(chapter: Chapter, manga: Manga): Boolean {
        if (!readerPreferences.skipFiltered().get()) {
            return false
        }

        return shouldSkipByUnreadFilter(chapter, manga) ||
            shouldSkipByDownloadFilter(chapter, manga) ||
            shouldSkipByBookmarkFilter(chapter, manga)
    }

    private fun shouldSkipByUnreadFilter(chapter: Chapter, manga: Manga): Boolean {
        return when (manga.unreadFilterRaw) {
            Manga.CHAPTER_SHOW_READ -> !chapter.read
            Manga.CHAPTER_SHOW_UNREAD -> chapter.read
            else -> false
        }
    }

    private fun shouldSkipByDownloadFilter(chapter: Chapter, manga: Manga): Boolean {
        val isDownloaded = downloadManager.isChapterDownloaded(
            chapter.name,
            chapter.scanlator,
            manga.title,
            manga.source,
        )

        return when (manga.downloadedFilterRaw) {
            Manga.CHAPTER_SHOW_DOWNLOADED -> !isDownloaded
            Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> isDownloaded
            else -> false
        }
    }

    private fun shouldSkipByBookmarkFilter(chapter: Chapter, manga: Manga): Boolean {
        return when (manga.bookmarkedFilterRaw) {
            Manga.CHAPTER_SHOW_BOOKMARKED -> !chapter.bookmark
            Manga.CHAPTER_SHOW_NOT_BOOKMARKED -> chapter.bookmark
            else -> false
        }
    }

    private fun processChapterList(
        chapters: List<Chapter>,
        selectedChapter: Chapter,
        manga: Manga,
    ): List<ReaderChapter> {
        return chapters
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .let { sorted ->
                if (readerPreferences.skipDupe().get()) {
                    sorted.removeDuplicates(selectedChapter)
                } else {
                    sorted
                }
            }
            .let { processed ->
                if (basePreferences.downloadedOnly().get()) {
                    processed.filterDownloadedChapters(manga)
                } else {
                    processed
                }
            }
            .map { it.toDbChapter() }
            .map(::ReaderChapter)
    }

    /**
     * Holds adjacent chapters for reader navigation.
     */
    data class AdjacentChapters(
        val current: ReaderChapter,
        val previous: ReaderChapter?,
        val next: ReaderChapter?,
    )
}
