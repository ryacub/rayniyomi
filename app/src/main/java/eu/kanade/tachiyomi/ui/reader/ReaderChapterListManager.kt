package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.items.chapter.model.toDbChapter
import eu.kanade.tachiyomi.data.database.models.manga.isRecognizedNumber
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.filterDownloadedChapters
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
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
    fun initChapterList(manga: Manga): List<ReaderChapter> {
        val chapters = runBlocking { getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true) }

        val selectedChapter = chapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader = when {
            (readerPreferences.skipRead().get() || readerPreferences.skipFiltered().get()) -> {
                val filteredChapters = chapters.filterNot {
                    when {
                        readerPreferences.skipRead().get() && it.read -> true
                        readerPreferences.skipFiltered().get() -> {
                            (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_READ && !it.read) ||
                                (manga.unreadFilterRaw == Manga.CHAPTER_SHOW_UNREAD && it.read) ||
                                (
                                    manga.downloadedFilterRaw ==
                                        Manga.CHAPTER_SHOW_DOWNLOADED &&
                                        !downloadManager.isChapterDownloaded(
                                            it.name,
                                            it.scanlator,
                                            manga.title,
                                            manga.source,
                                        )
                                    ) ||
                                (
                                    manga.downloadedFilterRaw ==
                                        Manga.CHAPTER_SHOW_NOT_DOWNLOADED &&
                                        downloadManager.isChapterDownloaded(
                                            it.name,
                                            it.scanlator,
                                            manga.title,
                                            manga.source,
                                        )
                                    ) ||
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_BOOKMARKED && !it.bookmark) ||
                                (manga.bookmarkedFilterRaw == Manga.CHAPTER_SHOW_NOT_BOOKMARKED && it.bookmark)
                        }
                        else -> false
                    }
                }

                if (filteredChapters.any { it.id == chapterId }) {
                    filteredChapters
                } else {
                    filteredChapters + listOf(selectedChapter)
                }
            }
            else -> chapters
        }

        _chapterList = chaptersForReader
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .run {
                if (readerPreferences.skipDupe().get()) {
                    removeDuplicates(selectedChapter)
                } else {
                    this
                }
            }
            .run {
                if (basePreferences.downloadedOnly().get()) {
                    filterDownloadedChapters(manga)
                } else {
                    this
                }
            }
            .map { it.toDbChapter() }
            .map(::ReaderChapter)

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
     * Get the adjacent chapters (current, previous, next) for the given chapter.
     * Returns a Triple of (current, previous, next) where previous/next may be null at boundaries.
     */
    fun getAdjacentChapters(chapter: ReaderChapter): Triple<ReaderChapter, ReaderChapter?, ReaderChapter?> {
        val chapterPos = chapterList.indexOf(chapter)
        return Triple(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
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
}
