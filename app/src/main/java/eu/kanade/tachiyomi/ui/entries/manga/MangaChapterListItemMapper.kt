package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.translation.TranslationState
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.source.local.entries.manga.isLocal

internal class MangaChapterListItemMapper(
    private val downloadManager: MangaDownloadManager,
) {

    fun map(
        chapters: List<Chapter>,
        manga: Manga,
        selectedChapterIds: Set<Long>,
        translationStates: Map<Long, TranslationState>,
    ): List<ChapterList.Item> {
        val isLocal = manga.isLocal()
        return chapters.map { chapter ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(chapter.id)
            }
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    manga.title,
                    manga.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> MangaDownload.State.DOWNLOADED
                else -> MangaDownload.State.NOT_DOWNLOADED
            }

            ChapterList.Item(
                chapter = chapter,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = chapter.id in selectedChapterIds,
                translationState = translationStateOf(translationStates, chapter.id),
            )
        }
    }
}
