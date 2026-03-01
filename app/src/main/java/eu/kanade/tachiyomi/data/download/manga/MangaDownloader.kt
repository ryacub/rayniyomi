package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import aniyomi.util.DataSaver
import aniyomi.util.DataSaver.Companion.getImage
import com.hippo.unifile.UniFile
import eu.kanade.domain.entries.manga.model.getComicInfo
import eu.kanade.domain.items.chapter.model.toSChapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.core.DownloadQueueOperations
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.model.DownloadBlockedReason
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadStatusTracker
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateNotifier
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.DiskUtil.NOMEDIA_FILE
import eu.kanade.tachiyomi.util.storage.saveTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.archive.ZipWriter
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Response
import okio.Throttler
import okio.buffer
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNow
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.comicinfo.COMIC_INFO_FILE
import tachiyomi.core.metadata.comicinfo.ComicInfo
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Locale

/**
 * This class is the one in charge of downloading chapters.
 *
 * Its queue contains the list of chapters to download. In order to download them, the downloader
 * subscription must be running and the list of chapters must be sent to them by [downloadsRelay].
 *
 * The queue manipulation must be done in one thread (currently the main thread) to avoid unexpected
 * behavior, but it's safe to read it from multiple threads.
 */
class MangaDownloader(
    private val context: Context,
    private val provider: MangaDownloadProvider,
    private val cache: MangaDownloadCache,
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val chapterCache: ChapterCache = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val xml: XML = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val getMangaTracks: GetMangaTracks = Injekt.get(),
    // SY -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // SY <--
) {

    /**
     * Queue where active downloads are kept.
     */
    private val _queueState = MutableStateFlow<List<MangaDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    /**
     * Store for persisting downloads across restarts.
     */
    private val store = MangaDownloadStore(context)

    /**
     * Shared queue mutation operations.
     */
    private val queueOps = DownloadQueueOperations(
        _queueState = _queueState,
        store = store,
        itemId = { it.chapter.id },
        isActive = {
            it.status == MangaDownload.State.DOWNLOADING ||
                it.status == MangaDownload.State.QUEUE
        },
        markQueued = {
            it.status = MangaDownload.State.QUEUE
            it.displayStatus = DownloadDisplayStatus.WAITING_FOR_SLOT
            it.blockedReason = DownloadBlockedReason.SLOT
        },
        markInactive = {
            if (it.status == MangaDownload.State.DOWNLOADING ||
                it.status == MangaDownload.State.QUEUE
            ) {
                it.status = MangaDownload.State.NOT_DOWNLOADED
                it.displayStatus = DownloadDisplayStatus.PREPARING
                it.blockedReason = null
            }
        },
    )

    /**
     * Notifier for the downloader state and progress.
     */
    private val notifier by lazy { MangaDownloadNotifier(context) }

    /**
     * The throttler used to control the download speed.
     */
    private val throttler = Throttler()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloaderJob: Job? = null

    /**
     * Whether the downloader is running.
     */
    val isRunning: Boolean
        get() = downloaderJob?.isActive ?: false

    /**
     * Whether the downloader is paused
     */
    @Volatile
    var isPaused: Boolean = false

    init {
        launchNow {
            val chapters = async { store.restore() }
            addAllToQueue(chapters.await())
        }
    }

    /**
     * Starts the downloader. It doesn't do anything if it's already running or there isn't anything
     * to download.
     *
     * @return true if the downloader is started, false otherwise.
     */
    fun start(): Boolean {
        if (isRunning || queueState.value.isEmpty()) {
            return false
        }

        val pending = queueState.value.filter { it.status != MangaDownload.State.DOWNLOADED }
        pending.forEach {
            if (it.status != MangaDownload.State.QUEUE) it.status = MangaDownload.State.QUEUE
            it.displayStatus = DownloadDisplayStatus.WAITING_FOR_SLOT
            it.blockedReason = DownloadBlockedReason.SLOT
            it.retryAttempt = 0
            it.lastErrorCode = null
            it.lastErrorReason = null
        }

        isPaused = false

        launchDownloaderJob()
        notifier.onQueueStatusSummary(queueState.value)

        return pending.isNotEmpty()
    }

    /**
     * Stops the downloader.
     */
    fun stop(
        reason: String? = null,
        blockedStatus: DownloadDisplayStatus? = null,
    ) {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach {
                when (blockedStatus) {
                    DownloadDisplayStatus.WAITING_FOR_NETWORK -> {
                        it.status = MangaDownload.State.QUEUE
                        it.displayStatus = DownloadDisplayStatus.WAITING_FOR_NETWORK
                        it.blockedReason = DownloadBlockedReason.NETWORK
                    }
                    DownloadDisplayStatus.WAITING_FOR_WIFI -> {
                        it.status = MangaDownload.State.QUEUE
                        it.displayStatus = DownloadDisplayStatus.WAITING_FOR_WIFI
                        it.blockedReason = DownloadBlockedReason.WIFI
                    }
                    else -> {
                        it.status = MangaDownload.State.ERROR
                        it.displayStatus = DownloadDisplayStatus.FAILED
                        it.blockedReason = null
                    }
                }
            }

        notifier.onQueueStatusSummary(queueState.value)

        if (reason != null) {
            notifier.onWarning(reason)
            return
        }

        if (isPaused && queueState.value.isNotEmpty()) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        isPaused = false

        MangaDownloadJob.stop(context)
    }

    /**
     * Pauses the downloader
     */
    fun pause() {
        cancelDownloaderJob()
        queueState.value
            .filter { it.status == MangaDownload.State.DOWNLOADING }
            .forEach {
                it.status = MangaDownload.State.QUEUE
                it.displayStatus = DownloadDisplayStatus.PAUSED_BY_USER
                it.blockedReason = null
            }
        isPaused = true
        notifier.onQueueStatusSummary(queueState.value)
    }

    /**
     * Removes everything from the queue.
     */
    fun clearQueue() {
        cancelDownloaderJob()

        internalClearQueue()
        notifier.dismissProgress()
    }

    /**
     * Prepares the subscriptions to start downloading.
     */
    private fun launchDownloaderJob() {
        if (isRunning) return

        downloaderJob = scope.launch {
            val activeDownloadsFlow = queueState.transformLatest { queue ->
                while (true) {
                    val activeDownloads = queue.asSequence()
                        .filter {
                            it.status.value <= MangaDownload.State.DOWNLOADING.value &&
                                it.displayStatus != DownloadDisplayStatus.PAUSED_LOW_STORAGE &&
                                it.displayStatus != DownloadDisplayStatus.WAITING_FOR_NETWORK &&
                                it.displayStatus != DownloadDisplayStatus.WAITING_FOR_WIFI &&
                                it.displayStatus != DownloadDisplayStatus.PAUSED_BY_USER
                        } // Ignore completed downloads, leave them in the queue
                        .sortedWith(
                            compareByDescending<MangaDownload> { it.priority.value }
                                .thenBy { queue.indexOf(it) },
                        ) // Sort by priority first, then queue position
                        .groupBy { it.source }
                        .toList().take(getDownloadSlots()) // Concurrently download from configured source slots
                        .map { (_, downloads) -> downloads.first() }

                    val activeSet = activeDownloads.toSet()
                    queue.filter { it.status == MangaDownload.State.QUEUE && it !in activeSet }
                        .forEach { queued ->
                            if (queued.displayStatus != DownloadDisplayStatus.WAITING_FOR_NETWORK &&
                                queued.displayStatus != DownloadDisplayStatus.WAITING_FOR_WIFI &&
                                queued.displayStatus != DownloadDisplayStatus.PAUSED_BY_USER &&
                                queued.displayStatus != DownloadDisplayStatus.PAUSED_LOW_STORAGE
                            ) {
                                queued.displayStatus = DownloadDisplayStatus.WAITING_FOR_SLOT
                                queued.blockedReason = DownloadBlockedReason.SLOT
                            }
                        }

                    notifier.onQueueStatusSummary(queue)
                    emit(activeDownloads)

                    if (activeDownloads.isEmpty()) break
                    // Suspend until a download enters the ERROR state
                    val activeDownloadsErroredFlow =
                        combine(activeDownloads.map(MangaDownload::statusFlow)) { states ->
                            states.contains(MangaDownload.State.ERROR)
                        }.filter { it }
                    activeDownloadsErroredFlow.first()
                }
            }.distinctUntilChanged()

            // Use supervisorScope to cancel child jobs when the downloader job is cancelled
            supervisorScope {
                val downloadJobs = mutableMapOf<MangaDownload, Job>()

                activeDownloadsFlow.collectLatest { activeDownloads ->
                    val downloadJobsToStop = downloadJobs.filter { it.key !in activeDownloads }
                    downloadJobsToStop.forEach { (download, job) ->
                        job.cancel()
                        downloadJobs.remove(download)
                    }

                    val downloadsToStart = activeDownloads.filter { it !in downloadJobs }
                    downloadsToStart.forEach { download ->
                        downloadJobs[download] = launchDownloadJob(download)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.launchDownloadJob(download: MangaDownload) = launchIO {
        try {
            downloadChapter(download)

            // Remove successful download from queue
            if (download.status == MangaDownload.State.DOWNLOADED) {
                removeFromQueue(download)
            }
            if (areAllDownloadsFinished()) {
                stop()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e)
            download.status = MangaDownload.State.ERROR
            download.displayStatus = DownloadDisplayStatus.FAILED
            download.lastErrorCode = e::class.simpleName
            download.lastErrorReason = e.message
            notifier.onError(e.message)
            stop()
        }
    }

    /**
     * Destroys the downloader subscriptions.
     */
    private fun cancelDownloaderJob() {
        downloaderJob?.cancel()
        downloaderJob = null
    }

    /**
     * Creates a download object for every chapter and adds them to the downloads queue.
     *
     * @param manga the manga of the chapters to download.
     * @param chapters the list of chapters to download.
     * @param autoStart whether to start the downloader after enqueing the chapters.
     */
    fun queueChapters(manga: Manga, chapters: List<Chapter>, autoStart: Boolean) {
        if (chapters.isEmpty()) return

        val source = sourceManager.get(manga.source) as? HttpSource ?: return
        val wasEmpty = queueState.value.isEmpty()
        val downloadedChapterNames = provider.findMangaDir(manga.title, source)
            ?.listFiles()
            .orEmpty()
            .mapNotNull { it.name }
            .toSet()
        val chaptersToQueue = chapters.asSequence()
            // Filter out those already downloaded.
            .filter { chapter ->
                provider.getValidChapterDirNames(chapter.name, chapter.scanlator)
                    .none { it in downloadedChapterNames }
            }
            // Add chapters to queue from the start.
            .sortedByDescending { it.sourceOrder }
            // Filter out those already enqueued.
            .filter { chapter -> queueState.value.none { it.chapter.id == chapter.id } }
            // Create a download for each one.
            .map { MangaDownload(source, manga, it) }
            .toList()

        if (chaptersToQueue.isNotEmpty()) {
            addAllToQueue(chaptersToQueue)
            notifier.onQueueStatusSummary(queueState.value)

            // Start downloader if needed
            if (autoStart && wasEmpty) {
                val queuedDownloads = queueState.value.count { it: MangaDownload -> it.source !is UnmeteredSource }
                val maxDownloadsFromSource = queueState.value
                    .groupBy { it.source }
                    .filterKeys { it !is UnmeteredSource }
                    .maxOfOrNull { it.value.size }
                    ?: 0
                if (
                    queuedDownloads > DOWNLOADS_QUEUED_WARNING_THRESHOLD ||
                    maxDownloadsFromSource > CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD
                ) {
                    notifier.onWarning(
                        context.stringResource(AYMR.strings.download_queue_size_warning),
                        WARNING_NOTIF_TIMEOUT_MS,
                        NotificationHandler.openUrl(context, MangaLibraryUpdateNotifier.HELP_WARNING_URL),
                    )
                }
                MangaDownloadJob.start(context)
            }
        }
    }

    /**
     * Downloads a chapter.
     *
     * @param download the chapter to be downloaded.
     */
    private suspend fun downloadChapter(download: MangaDownload) {
        download.displayStatus = DownloadDisplayStatus.PREPARING
        download.blockedReason = DownloadBlockedReason.PREPARING
        val mangaDir = provider.getMangaDir(download.manga.title, download.source)

        val availSpace = DiskUtil.getAvailableStorageSpace(mangaDir)
        if (availSpace != -1L && availSpace < MIN_DISK_SPACE) {
            download.status = MangaDownload.State.QUEUE
            download.displayStatus = DownloadDisplayStatus.PAUSED_LOW_STORAGE
            download.blockedReason = DownloadBlockedReason.STORAGE
            download.lastErrorCode = "LOW_STORAGE"
            download.lastErrorReason = context.stringResource(AYMR.strings.download_insufficient_space)
            notifier.onWarning(
                context.stringResource(AYMR.strings.download_insufficient_space),
                download.manga.id,
            )
            return
        }

        val chapterDirname = provider.getChapterDirName(download.chapter.name, download.chapter.scanlator)
        val tmpDir = mangaDir.createDirectory(chapterDirname + TMP_DIR_SUFFIX)!!

        try {
            // If the page list already exists, start from the file
            val pageList = download.pages ?: run {
                // Otherwise, pull page list from network and add them to download object
                val pages = download.source.getPageList(download.chapter.toSChapter())

                if (pages.isEmpty()) {
                    throw Exception(context.stringResource(MR.strings.page_list_empty_error))
                }
                // Don't trust index from source
                val reIndexedPages = pages.mapIndexed { index, page ->
                    Page(
                        index,
                        page.url,
                        page.imageUrl,
                        page.uri,
                    )
                }
                download.pages = reIndexedPages
                reIndexedPages
            }

            val dataSaver = if (sourcePreferences.dataSaverDownloader().get()) {
                DataSaver(download.source, sourcePreferences)
            } else {
                DataSaver.NoOp
            }

            // Delete all temporary (unfinished) files
            tmpDir.listFiles()
                ?.filter { it.extension == "tmp" }
                ?.forEach { it.delete() }

            download.status = MangaDownload.State.DOWNLOADING
            download.displayStatus = DownloadDisplayStatus.CONNECTING
            download.blockedReason = null
            download.lastProgressAt = System.currentTimeMillis()
            download.retryAttempt = 0

            // Start downloading images, consider we can have downloaded images already
            // Concurrency is configurable via preferences (default: 4, range: 1-6)
            val concurrency = downloadPreferences.pageDownloadConcurrency().get()
            var progressJob: Job? = null
            var stallMonitorJob: Job? = null
            try {
                coroutineScope {
                    progressJob = launch {
                        download.progressFlow.collect {
                            if (download.status != MangaDownload.State.DOWNLOADING) return@collect
                            download.lastProgressAt = System.currentTimeMillis()
                            download.retryAttempt = 0
                            download.displayStatus = DownloadDisplayStatus.DOWNLOADING
                            notifier.onProgressChange(download)
                        }
                    }
                    stallMonitorJob = launch {
                        while (download.status == MangaDownload.State.DOWNLOADING) {
                            delay(1_000)
                            val now = System.currentTimeMillis()
                            if (DownloadStatusTracker.shouldMarkStalled(download, now)) {
                                download.displayStatus = DownloadDisplayStatus.STALLED
                                notifier.onProgressChange(download)
                            }
                        }
                    }

                    pageList.asFlow()
                        .flatMapMerge(concurrency = concurrency) { page ->
                            flow {
                                // Fetch image URL if necessary
                                if (page.imageUrl.isNullOrEmpty()) {
                                    page.status = Page.State.LOAD_PAGE
                                    try {
                                        page.imageUrl = download.source.getImageUrl(page)
                                    } catch (e: Throwable) {
                                        page.status = Page.State.ERROR
                                    }
                                }

                                withIOContext { getOrDownloadImage(page, download, tmpDir, dataSaver) }
                                emit(page)
                            }.flowOn(Dispatchers.IO)
                        }
                        .collect { }
                }
            } finally {
                withContext(NonCancellable) {
                    stallMonitorJob?.cancel()
                    stallMonitorJob?.join()
                    progressJob?.cancel()
                    progressJob?.join()
                }
            }

            // Do after download completes
            if (!isDownloadSuccessful(download, tmpDir)) {
                download.status = MangaDownload.State.ERROR
                download.displayStatus = DownloadDisplayStatus.FAILED
                download.lastErrorCode = "INCOMPLETE"
                download.lastErrorReason = "Incomplete chapter output"
                return
            }
            download.displayStatus = DownloadDisplayStatus.VERIFYING

            createComicInfoFile(
                tmpDir,
                download.manga,
                download.chapter,
                download.source,
            )

            // Only rename the directory if it's downloaded
            if (downloadPreferences.saveChaptersAsCBZ().get()) {
                archiveChapter(mangaDir, chapterDirname, tmpDir)
            } else {
                tmpDir.renameTo(chapterDirname)
            }
            cache.addChapter(chapterDirname, mangaDir, download.manga)

            DiskUtil.createNoMediaFile(tmpDir, context)

            download.status = MangaDownload.State.DOWNLOADED
            download.displayStatus = DownloadDisplayStatus.COMPLETED
            download.lastErrorCode = null
            download.lastErrorReason = null
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is LowStorageException) {
                return
            }
            // If the page list threw, it will resume here
            logcat(LogPriority.ERROR, error)
            download.status = MangaDownload.State.ERROR
            download.displayStatus = DownloadDisplayStatus.FAILED
            download.lastErrorCode = error::class.simpleName
            download.lastErrorReason = error.message
            notifier.onError(error.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Gets the image from the filesystem if it exists or downloads it otherwise.
     *
     * @param page the page to download.
     * @param download the download of the page.
     * @param tmpDir the temporary directory of the download.
     */
    private suspend fun getOrDownloadImage(
        page: Page,
        download: MangaDownload,
        tmpDir: UniFile,
        dataSaver: DataSaver,
    ) {
        // If the image URL is empty, do nothing
        if (page.imageUrl == null) {
            return
        }

        val digitCount = (download.pages?.size ?: 0).toString().length.coerceAtLeast(3)
        val filename = "%0${digitCount}d".format(Locale.ENGLISH, page.number)
        val tmpFile = tmpDir.findFile("$filename.tmp")

        // Delete temp file if it exists
        tmpFile?.delete()

        // Try to find the image file
        val imageFile = tmpDir.listFiles()?.firstOrNull {
            it.name!!.startsWith("$filename.") ||
                it.name!!.startsWith(
                    "${filename}__001",
                )
        }

        try {
            // If the image is already downloaded, do nothing. Otherwise download from network
            val file = when {
                imageFile != null -> imageFile
                chapterCache.isImageInCache(page.imageUrl!!) -> copyImageFromCache(
                    chapterCache.getImageFile(page.imageUrl!!),
                    tmpDir,
                    filename,
                )
                else -> downloadImage(page, download, download.source, tmpDir, filename, dataSaver)
            }

            // When the page is ready, set page path, progress (just in case) and status
            splitTallImageIfNeeded(page, tmpDir)
            page.uri = file.uri
            page.progress = 100
            page.status = Page.State.READY
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (e is LowStorageException) throw e
            // Mark this page as error and allow to download the remaining
            page.progress = 0
            page.status = Page.State.ERROR
            download.lastErrorCode = e::class.simpleName
            download.lastErrorReason = e.message
            notifier.onError(e.message, download.chapter.name, download.manga.title, download.manga.id)
        }
    }

    /**
     * Downloads the image from network to a file in tmpDir.
     *
     * @param page the page to download.
     * @param source the source of the page.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private suspend fun downloadImage(
        page: Page,
        download: MangaDownload,
        source: HttpSource,
        tmpDir: UniFile,
        filename: String,
        dataSaver: DataSaver,
    ): UniFile {
        page.status = Page.State.DOWNLOAD_IMAGE
        page.progress = 0
        return flow {
            val response = source.getImage(page, dataSaver)
            val file = tmpDir.createFile("$filename.tmp")!!
            try {
                throttler.apply {
                    bytesPerSecond(downloadPreferences.downloadSpeedLimit().get().toLong() * 1024)
                }
                val throttledSource = throttler.source(response.body.source()).buffer()
                throttledSource.saveTo(file.openOutputStream())
                throttledSource.close()
                val extension = getImageExtension(response, file)
                file.renameTo("$filename.$extension")
            } catch (e: Exception) {
                response.close()
                file.delete()
                if (isLowStorageFailure(e.message)) {
                    download.status = MangaDownload.State.QUEUE
                    download.displayStatus = DownloadDisplayStatus.PAUSED_LOW_STORAGE
                    download.blockedReason = DownloadBlockedReason.STORAGE
                    download.lastErrorCode = "LOW_STORAGE"
                    download.lastErrorReason = e.message
                    notifier.onWarning(
                        context.stringResource(AYMR.strings.download_insufficient_space),
                        mangaId = download.manga.id,
                    )
                    throw LowStorageException(e.message ?: "Insufficient storage")
                }
                throw e
            }
            emit(file)
        }
            // Retry 3 times, waiting 2, 4 and 8 seconds between attempts.
            .retryWhen { cause, attempt ->
                if (cause is LowStorageException) {
                    return@retryWhen false
                }
                if (attempt < 3) {
                    download.retryAttempt = attempt.toInt() + 1
                    download.displayStatus = DownloadDisplayStatus.RETRYING
                    delay((2L shl attempt.toInt()) * 1000)
                    true
                } else {
                    download.lastErrorCode = "RETRY_EXHAUSTED"
                    download.lastErrorReason = "Network retries exhausted"
                    false
                }
            }
            .flowOn(Dispatchers.IO)
            .first()
    }

    /**
     * Copies the image from cache to file in tmpDir.
     *
     * @param cacheFile the file from cache.
     * @param tmpDir the temporary directory of the download.
     * @param filename the filename of the image.
     */
    private fun copyImageFromCache(cacheFile: File, tmpDir: UniFile, filename: String): UniFile {
        val tmpFile = tmpDir.createFile("$filename.tmp")!!
        cacheFile.inputStream().use { input ->
            tmpFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        val extension = ImageUtil.findImageType(cacheFile.inputStream()) ?: return tmpFile
        tmpFile.renameTo("$filename.${extension.extension}")
        cacheFile.delete()
        return tmpFile
    }

    /**
     * Returns the extension of the downloaded image from the network response, or if it's null,
     * analyze the file. If everything fails, assume it's a jpg.
     *
     * @param response the network response of the image.
     * @param file the file where the image is already downloaded.
     */
    private fun getImageExtension(response: Response, file: UniFile): String {
        val mime = response.body.contentType()?.run { if (type == "image") "image/$subtype" else null }
        return ImageUtil.getExtensionFromMimeType(mime) { file.openInputStream() }
    }

    private fun splitTallImageIfNeeded(page: Page, tmpDir: UniFile) {
        if (!downloadPreferences.splitTallImages().get()) return

        try {
            val filenamePrefix = "%03d".format(Locale.ENGLISH, page.number)
            val imageFile = tmpDir.listFiles()?.firstOrNull { it.name.orEmpty().startsWith(filenamePrefix) }
                ?: error(context.stringResource(MR.strings.download_notifier_split_page_not_found, page.number))

            // If the original page was previously split, then skip
            if (imageFile.name.orEmpty().startsWith("${filenamePrefix}__")) return

            ImageUtil.splitTallImage(tmpDir, imageFile, filenamePrefix)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to split downloaded image" }
        }
    }

    /**
     * Checks if the download was successful.
     *
     * @param download the download to check.
     * @param tmpDir the directory where the download is currently stored.
     */
    private fun isDownloadSuccessful(
        download: MangaDownload,
        tmpDir: UniFile,
    ): Boolean {
        // Page list hasn't been initialized
        val downloadPageCount = download.pages?.size ?: return false
        // Ensure that all pages have been downloaded
        if (download.downloadedImages != downloadPageCount) {
            return false
        }
        // Ensure that the chapter folder has all the pages
        val downloadedImagesCount = tmpDir.listFiles().orEmpty().count {
            val fileName = it.name.orEmpty()
            when {
                fileName in listOf(COMIC_INFO_FILE, NOMEDIA_FILE) -> false
                fileName.endsWith(".tmp") -> false
                // Only count the first split page and not the others
                fileName.contains("__") && !fileName.endsWith("__001.jpg") -> false
                else -> true
            }
        }
        return downloadedImagesCount == downloadPageCount
    }

    /**
     * Archive the chapter pages as a CBZ.
     */
    private fun archiveChapter(
        mangaDir: UniFile,
        dirname: String,
        tmpDir: UniFile,
    ) {
        val zip = mangaDir.createFile("$dirname.cbz$TMP_DIR_SUFFIX")!!
        ZipWriter(context, zip).use { writer ->
            tmpDir.listFiles()?.forEach { file ->
                writer.write(file)
            }
        }
        zip.renameTo("$dirname.cbz")
        tmpDir.delete()
    }

    /**
     * Creates a ComicInfo.xml file inside the given directory.
     */
    private suspend fun createComicInfoFile(
        dir: UniFile,
        manga: Manga,
        chapter: Chapter,
        source: HttpSource,
    ) {
        val categories = getCategories.await(manga.id).map { it.name.trim() }.takeUnless { it.isEmpty() }
        val urls = getMangaTracks.await(manga.id)
            .mapNotNull { track ->
                track.remoteUrl.takeUnless { url -> url.isBlank() }?.trim()
            }
            .plus(source.getChapterUrl(chapter.toSChapter()).trim())
            .distinct()

        val comicInfo = getComicInfo(
            manga,
            chapter,
            urls,
            categories,
            source.name,
        )

        // Remove the old file
        dir.findFile(COMIC_INFO_FILE)?.delete()
        dir.createFile(COMIC_INFO_FILE)!!.openOutputStream().use {
            val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
            it.write(comicInfoString.toByteArray())
        }
    }

    /**
     * Returns true if all the queued downloads are in DOWNLOADED or ERROR state.
     */
    private fun areAllDownloadsFinished(): Boolean {
        return queueState.value.none { it.status.value <= MangaDownload.State.DOWNLOADING.value }
    }

    private fun getDownloadSlots(): Int {
        return downloadPreferences.numberOfDownloads().get().coerceIn(1, 5)
    }

    private fun addAllToQueue(downloads: List<MangaDownload>) = queueOps.addAll(downloads)

    private fun removeFromQueue(download: MangaDownload) = queueOps.remove(download)

    fun removeFromQueue(chapters: List<Chapter>) {
        val chapterIds = chapters.map { it.id }
        queueOps.removeIf { it.chapter.id in chapterIds }
    }

    fun removeFromQueue(manga: Manga) {
        queueOps.removeIf { it.manga.id == manga.id }
    }

    private fun internalClearQueue() = queueOps.internalClear()

    fun addToStartOfQueue(downloads: List<MangaDownload>) = queueOps.addToStart(downloads)

    fun moveToFront(download: MangaDownload) = queueOps.moveToFront(download)

    fun updateQueue(downloads: List<MangaDownload>) {
        val wasRunning = isRunning

        if (downloads.isEmpty()) {
            clearQueue()
            stop()
            return
        }

        pause()
        internalClearQueue()
        addAllToQueue(downloads)

        if (wasRunning) {
            start()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
        const val WARNING_NOTIF_TIMEOUT_MS = 30_000L
        const val CHAPTERS_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 15
        private const val DOWNLOADS_QUEUED_WARNING_THRESHOLD = 30
    }
}

// Arbitrary minimum required space to start a download: 200 MB
private const val MIN_DISK_SPACE = 200L * 1024 * 1024

private class LowStorageException(message: String) : Exception(message)

private fun isLowStorageFailure(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return message.contains("No space left on device", ignoreCase = true) ||
        message.contains("ENOSPC", ignoreCase = true) ||
        message.contains("disk full", ignoreCase = true) ||
        message.contains("insufficient storage", ignoreCase = true)
}
