package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import mihon.core.archive.archiveReader
import tachiyomi.domain.entries.manga.model.Manga
import java.io.InputStream

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: MangaSource,
    private val downloadManager: MangaDownloadManager,
    private val downloadProvider: MangaDownloadProvider,
    private val context: Context,
    private val readerPreferences: ReaderPreferences,
    private val translationPreferences: TranslationPreferences,
    private val translationStorageManager: TranslationStorageManager,
) : PageLoader() {

    private var archivePageLoader: ArchivePageLoader? = null

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val dbChapter = chapter.chapter
        val chapterPath = downloadProvider.findChapterDir(
            dbChapter.name,
            dbChapter.scanlator,
            manga.title,
            source,
        )
        val translatedFiles = if (readerPreferences.showTranslatedPages().get()) {
            translationStorageManager.getTranslatedPageFiles(
                dbChapter.name,
                dbChapter.scanlator,
                manga.title,
                source,
                translationPreferences.targetLanguage().get(),
            )
        } else {
            emptyMap()
        }
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath, translatedFiles)
        } else {
            getPagesFromDirectory(translatedFiles)
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile, translatedFiles: Map<Int, UniFile>): List<ReaderPage> {
        val loader = archivePageLoader ?: ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        val pages = loader.getPages()
        return substituteTranslatedPages(
            pages,
            translatedFiles.isNotEmpty(),
            translatedFiles::get,
            { translatedFile -> context.contentResolver.openInputStream(translatedFile.uri) },
        )
    }

    private suspend fun getPagesFromDirectory(translatedFiles: Map<Int, UniFile>): List<ReaderPage> {
        return downloadManager.buildPageList(
            source,
            manga,
            chapter.chapter.toDomainChapter(),
        ) { pages ->
            pages.map { page ->
                val translatedFile = translatedFiles[page.index]

                ReaderPage(page.index) {
                    translatedFile?.let {
                        runCatching { context.contentResolver.openInputStream(it.uri) }.getOrNull()
                    }
                        ?: page.openStream()
                        ?: throw IllegalStateException("No content for page ${page.index}")
                }.apply {
                    status = Page.State.READY
                }
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}

/**
 * Replace pages of an archive chapter with their stored translated files.
 *
 * A page without a stored translation keeps its original stream, so a partly
 * translated chapter still reads. A translated file that fails to open also
 * falls back to the original stream.
 */
internal fun substituteTranslatedPages(
    pages: List<ReaderPage>,
    showTranslated: Boolean,
    lookup: (Int) -> UniFile?,
    open: (UniFile) -> InputStream?,
): List<ReaderPage> {
    if (!showTranslated) return pages

    return pages.map { page ->
        val originalStream = page.stream
        val translatedFile = lookup(page.index) ?: return@map page

        ReaderPage(page.index) {
            try {
                open(translatedFile)
            } catch (_: Exception) {
                null
            } ?: originalStream?.invoke()
                ?: throw IllegalStateException("No content for page ${page.index}")
        }.apply {
            status = Page.State.READY
        }
    }
}
