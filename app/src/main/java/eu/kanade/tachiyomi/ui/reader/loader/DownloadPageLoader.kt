package eu.kanade.tachiyomi.ui.reader.loader

import android.app.Application
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
import uy.kohesive.injekt.injectLazy

/**
 * Loader used to load a chapter from the downloaded chapters.
 */
internal class DownloadPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val source: MangaSource,
    private val downloadManager: MangaDownloadManager,
    private val downloadProvider: MangaDownloadProvider,
) : PageLoader() {

    private val context: Application by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val translationPreferences: TranslationPreferences by injectLazy()
    private val translationStorageManager: TranslationStorageManager by injectLazy()

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
        return if (chapterPath?.isFile == true) {
            getPagesFromArchive(chapterPath)
        } else {
            getPagesFromDirectory()
        }
    }

    override fun recycle() {
        super.recycle()
        archivePageLoader?.recycle()
    }

    private suspend fun getPagesFromArchive(file: UniFile): List<ReaderPage> {
        val loader = ArchivePageLoader(file.archiveReader(context)).also { archivePageLoader = it }
        return loader.getPages()
    }

    private fun getPagesFromDirectory(): List<ReaderPage> {
        val pages = downloadManager.buildPageList(
            source,
            manga,
            chapter.chapter.toDomainChapter()!!,
        )
        val showTranslated = readerPreferences.showTranslatedPages().get()
        val targetLang = translationPreferences.targetLanguage().get()
        val dbChapter = chapter.chapter

        return pages.map { page ->
            val translatedFile = if (showTranslated) {
                translationStorageManager.getTranslatedPageFile(
                    dbChapter.name,
                    dbChapter.scanlator,
                    manga.title,
                    source,
                    targetLang,
                    page.index,
                )
            } else {
                null
            }

            val uri = translatedFile?.uri ?: page.uri
            ReaderPage(page.index, page.url, page.imageUrl) {
                uri?.let { context.contentResolver.openInputStream(it) }
                    ?: throw IllegalStateException("No URI for page ${page.index}")
            }.apply {
                status = Page.State.READY
            }
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        archivePageLoader?.loadPage(page)
    }
}
