package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.entries.manga.MangaChapterSequence
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.filterDownloadedChapters
import eu.kanade.tachiyomi.util.chapter.removeDuplicates
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository
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
    private val getManga: GetManga,
    private val mangaSourceLinkRepository: MangaSourceLinkRepository,
    private val downloadManager: MangaDownloadManager,
    private val readerPreferences: ReaderPreferences,
    private val basePreferences: BasePreferences,
) {
    private var _chapterList: List<ReaderChapter> = emptyList()
    private var chapterGroups: List<List<ReaderChapter>> = emptyList()
    private var readerMangas: List<Manga> = emptyList()
    private var mangaSourceLink: MangaSourceLink? = null

    val chapterList: List<ReaderChapter>
        get() = _chapterList

    val mangas: List<Manga>
        get() = readerMangas

    val isLinked: Boolean
        get() = mangaSourceLink != null

    var chapterId: Long = -1L

    /**
     * Initialize the chapter list for the given manga.
     * Retrieves chapters, applies filters (skip-read, skip-filtered, skip-dupe, downloaded-only),
     * sorts them, and converts to ReaderChapter objects.
     */
    suspend fun initChapterList(manga: Manga): List<ReaderChapter> {
        val link = mangaSourceLinkRepository.getForManga(manga.id)
        val partner = link?.let {
            val partnerId = if (it.firstMangaId == manga.id) it.secondMangaId else it.firstMangaId
            getManga.await(partnerId)
        }
        readerMangas = listOfNotNull(manga, partner).let { available ->
            if (link == null || partner == null) {
                listOf(manga)
            } else {
                listOf(available.first { it.id == link.firstMangaId }, available.first { it.id == link.secondMangaId })
            }
        }
        mangaSourceLink = link.takeIf { partner != null }

        val chaptersByOwner = readerMangas.associateWith { fetchChapters(it) }
        val selectedChapter = chaptersByOwner.values.flatten().find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")
        val processedByOwner = readerMangas.associateWith { owner ->
            val chapters = chaptersByOwner.getValue(owner)
            val selected = selectedChapter.takeIf { it.mangaId == owner.id }
            processChapterList(applyUserFilters(chapters, selected, owner), selected, owner)
        }
        chapterGroups = if (link == null || partner == null) {
            processedByOwner.getValue(manga).map(::listOf)
        } else {
            val firstManga = readerMangas.first { it.id == link.firstMangaId }
            val secondManga = readerMangas.first { it.id == link.secondMangaId }
            MangaChapterSequence.project(
                firstManga = firstManga,
                firstChapters = processedByOwner.getValue(firstManga).map { it.chapter },
                firstSourceName = "",
                secondManga = secondManga,
                secondChapters = processedByOwner.getValue(secondManga).map { it.chapter },
                secondSourceName = "",
                link = link,
                descending = false,
            ).map { group -> group.versions.map { ReaderChapter(it.chapter) } }
        }
        _chapterList = chapterGroups.flatten()
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
    fun getAdjacentChapters(
        chapter: ReaderChapter,
        isReadable: (ReaderChapter) -> Boolean = { true },
    ): AdjacentChapters {
        if (mangaSourceLink == null) {
            val chapterPos = chapterList.indexOfFirst { it.chapter.id == chapter.chapter.id }
            return AdjacentChapters(
                current = chapter,
                previous = chapterList.getOrNull(chapterPos - 1),
                next = chapterList.getOrNull(chapterPos + 1),
            )
        }

        val chapterPos = chapterGroups.indexOfFirst { group -> group.any { it.chapter.id == chapter.chapter.id } }
        val ownerId = getMangaForChapter(chapter).id
        fun preferred(group: List<ReaderChapter>?): ReaderChapter? =
            group?.filter(isReadable)?.let { readable ->
                readable.firstOrNull { getMangaForChapter(it).id == ownerId } ?: readable.firstOrNull()
            }

        fun findAdjacent(direction: Int): ReaderChapter? {
            var index = chapterPos + direction
            while (index in chapterGroups.indices) {
                preferred(chapterGroups[index])?.let { return it }
                index += direction
            }
            return null
        }

        return AdjacentChapters(
            current = chapter,
            previous = findAdjacent(-1),
            next = findAdjacent(1),
        )
    }

    fun getMangaForChapter(chapter: ReaderChapter): Manga =
        readerMangas.firstOrNull { it.id == chapter.chapter.mangaId }
            ?: error("No manga owner for chapter ${chapter.chapter.id}")

    /**
     * Get the chapter that should be deleted based on the current chapter position
     * and the removeAfterReadSlots preference.
     * Returns null if the chapter to delete is out of bounds.
     */
    fun getChapterForDeletion(currentChapter: ReaderChapter, removeAfterReadSlots: Int): ReaderChapter? {
        if (mangaSourceLink == null) {
            val currentChapterPosition = chapterList.indexOfFirst { it.chapter.id == currentChapter.chapter.id }
            return chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)
        }
        val currentChapterPosition = chapterGroups.indexOfFirst { group ->
            group.any { it.chapter.id == currentChapter.chapter.id }
        }
        val ownerId = getMangaForChapter(currentChapter).id
        val target = chapterGroups.getOrNull(currentChapterPosition - removeAfterReadSlots) ?: return null
        return target.firstOrNull { getMangaForChapter(it).id == ownerId }
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
                    chapter.mangaId == readerChapter.chapter.mangaId &&
                    !chapter.read &&
                    chapter.isRecognizedNumber &&
                    chapter.chapterNumber == readerChapter.chapter.chapterNumber
                ) {
                    ChapterUpdate(id = chapter.id, read = true)
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

    private fun applyUserFilters(
        chapters: List<Chapter>,
        selectedChapter: Chapter?,
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
        return if (selectedChapter == null || filteredChapters.any { it.id == selectedChapter.id }) {
            filteredChapters
        } else {
            filteredChapters + selectedChapter
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
        return when (manga.downloadedFilterRaw) {
            Manga.CHAPTER_SHOW_DOWNLOADED -> !isChapterDownloaded(chapter, manga)
            Manga.CHAPTER_SHOW_NOT_DOWNLOADED -> isChapterDownloaded(chapter, manga)
            else -> false
        }
    }

    private fun isChapterDownloaded(chapter: Chapter, manga: Manga): Boolean {
        return downloadManager.isChapterDownloaded(
            chapter.name,
            chapter.scanlator,
            manga.title,
            manga.source,
        )
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
        selectedChapter: Chapter?,
        manga: Manga,
    ): List<ReaderChapter> {
        return chapters
            .sortedWith(getChapterSort(manga, sortDescending = false))
            .let { sorted ->
                if (readerPreferences.skipDupe().get() && selectedChapter != null) {
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
