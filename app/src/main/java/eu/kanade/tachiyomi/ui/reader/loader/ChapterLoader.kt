package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import eu.kanade.domain.items.chapter.model.toSChapter
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import mihon.core.archive.archiveReader
import mihon.core.archive.epubReader
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.manga.LocalMangaSource
import tachiyomi.source.local.io.Format
import java.util.IdentityHashMap

/**
 * Loads the pages of each chapter of one reader. It is the only writer of a chapter's load
 * state: it runs at most one load attempt for each chapter, and only the newest attempt may
 * write [ReaderChapter.state] and [ReaderChapter.pageLoader].
 *
 * Attempts run in [scope], so a caller that stops waiting does not cancel a load that other
 * callers share.
 */
class ChapterLoader(
    private val context: Context,
    private val downloadManager: MangaDownloadManager,
    private val downloadProvider: MangaDownloadProvider,
    private val manga: Manga,
    private val source: MangaSource,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val readerPreferences: ReaderPreferences by lazy { appGraph.readerPreferences }
    private val translationPreferences: TranslationPreferences by lazy { appGraph.translationPreferences }
    private val translationStorageManager: TranslationStorageManager by lazy { appGraph.translationStorageManager }

    private val attemptScope = CoroutineScope(
        scope.coroutineContext + SupervisorJob(scope.coroutineContext.job) + ioDispatcher,
    )

    private val lock = Any()
    private val attempts = IdentityHashMap<ReaderChapter, Deferred<Unit>>()

    /**
     * Loads [chapter] and returns when its pages are installed. Joins the load in flight, if any.
     * Returns immediately if the chapter is already loaded.
     */
    suspend fun loadChapter(chapter: ReaderChapter) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val attempt = synchronized(lock) {
                attempts[chapter]?.takeUnless { it.isCompleted } ?: if (chapterIsReady(chapter)) {
                    return
                } else {
                    startAttempt(chapter, previous = null, reuseLoader = false)
                }
            }
            try {
                attempt.await()
                return
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (!attemptScope.isActive) throw e
                // A newer attempt replaced this one. Decide again.
            }
        }
    }

    /**
     * Loads the pages of [chapter] again, for example after the translated-pages preference
     * changed. Cancels the load in flight and waits for it to stop before the new load starts.
     * Reuses the chapter's page loader when it has one. If the chapter was loaded and the new
     * load fails, the chapter keeps its old pages and this function throws.
     */
    suspend fun reloadChapter(chapter: ReaderChapter) {
        val attempt = synchronized(lock) {
            startAttempt(chapter, previous = attempts[chapter], reuseLoader = true)
        }
        attempt.await()
    }

    /** Call with [lock] held. */
    private fun startAttempt(
        chapter: ReaderChapter,
        previous: Deferred<Unit>?,
        reuseLoader: Boolean,
    ): Deferred<Unit> {
        if (chapter.state !is ReaderChapter.State.Loaded) {
            chapter.state = ReaderChapter.State.Loading
        }
        val attempt = attemptScope.async(start = CoroutineStart.LAZY) {
            previous?.cancelAndJoin()
            runAttempt(chapter, reuseLoader)
        }
        attempts[chapter] = attempt
        attempt.invokeOnCompletion { cause ->
            synchronized(lock) {
                if (attempts[chapter] !== attempt) return@invokeOnCompletion
                attempts.remove(chapter)
                if (cause is CancellationException && chapter.state == ReaderChapter.State.Loading) {
                    chapter.state = ReaderChapter.State.Wait
                }
            }
        }
        attempt.start()
        return attempt
    }

    private suspend fun runAttempt(chapter: ReaderChapter, reuseLoader: Boolean) {
        val self = currentCoroutineContext().job
        fun isCurrent() = attempts[chapter] === self

        val reusedLoader = chapter.pageLoader?.takeIf { reuseLoader && !it.isRecycled }
        logcat { "Loading pages for ${chapter.chapter.name}" }
        try {
            val loader = reusedLoader ?: getPageLoader(chapter).also { loader ->
                synchronized(lock) {
                    if (isCurrent()) chapter.pageLoader = loader
                }
            }
            val pages = loader.getPages().onEach { it.chapter = chapter }
            if (pages.isEmpty()) {
                throw Exception(context.stringResource(MR.strings.page_list_empty_error))
            }
            synchronized(lock) {
                if (!isCurrent() || chapter.pageLoader !== loader || loader.isRecycled) {
                    throw CancellationException("A newer load replaced this one, or the chapter was released")
                }
                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (reusedLoader == null &&
                    (!chapter.chapter.read || readerPreferences.preserveReadingPosition().get())
                ) {
                    chapter.requestedPage = chapter.chapter.lastPageRead.toInt()
                }
                chapter.state = ReaderChapter.State.Loaded(pages)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            synchronized(lock) {
                if (isCurrent() && chapter.state !is ReaderChapter.State.Loaded) {
                    chapter.state = ReaderChapter.State.Error(e)
                }
            }
            throw e
        }
    }

    /**
     * Checks [chapter] to be loaded based on present pages and loader in addition to state.
     */
    private fun chapterIsReady(chapter: ReaderChapter): Boolean {
        return chapter.state is ReaderChapter.State.Loaded && chapter.pageLoader != null
    }

    /**
     * Returns the page loader to use for this [chapter].
     */
    private fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val dbChapter = chapter.chapter
        val isDownloaded = downloadManager.isChapterDownloaded(
            dbChapter.name,
            dbChapter.scanlator,
            manga.title,
            manga.source,
            skipCache = true,
        )
        return when {
            isDownloaded -> DownloadPageLoader(
                chapter,
                manga,
                source,
                downloadManager,
                downloadProvider,
                context,
                readerPreferences,
                translationPreferences,
                translationStorageManager,
            )
            source is LocalMangaSource -> source.getFormat(chapter.chapter.toSChapter()).let { format ->
                when (format) {
                    is Format.Directory -> DirectoryPageLoader(format.file)
                    is Format.Archive -> ArchivePageLoader(format.file.archiveReader(context))
                    is Format.Epub -> EpubPageLoader(format.file.epubReader(context))
                }
            }
            source is HttpSource -> HttpPageLoader(chapter, source)
            source is StubMangaSource -> error(
                context.stringResource(MR.strings.source_not_installed, source.toString()),
            )
            else -> error(context.stringResource(MR.strings.loader_not_implemented_error))
        }
    }
}
