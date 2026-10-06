package eu.kanade.tachiyomi.data.library.manga

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.library.AutoUpdateCandidate
import eu.kanade.tachiyomi.data.library.LibraryUpdateNotificationMode
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgress
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressTracker
import eu.kanade.tachiyomi.data.library.SkippedUpdate
import eu.kanade.tachiyomi.data.library.evaluateAutoUpdateCandidate
import eu.kanade.tachiyomi.data.library.skippedUpdatesForReport
import eu.kanade.tachiyomi.data.library.writeSkippedUpdateReport
import eu.kanade.tachiyomi.data.notification.ErrorLogWriteOutcome
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.notification.hasShareableErrorLogFile
import eu.kanade.tachiyomi.data.notification.writeErrorLogOutcome
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.source.manga.model.SourceNotInstalledException
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.i18n.MR
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MangaLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: MangaSourceManager = appGraph.mangaSourceManager
    private val libraryPreferences: LibraryPreferences = appGraph.libraryPreferences
    private val downloadManager: MangaDownloadManager = appGraph.mangaDownloadManager
    private val getLibraryManga: GetLibraryManga = appGraph.getLibraryManga
    private val getManga: GetManga = appGraph.getManga
    private val mangaFetchInterval: MangaFetchInterval = appGraph.mangaFetchInterval
    private val filterChaptersForDownload: FilterChaptersForDownload = appGraph.filterChaptersForDownload
    private val updateMangaFromRemote: UpdateMangaFromRemote = appGraph.updateMangaFromRemote

    private val notifier = MangaLibraryUpdateNotifier(
        context,
        notificationMode = if (WORK_NAME_MANUAL in tags) {
            LibraryUpdateNotificationMode.Live
        } else {
            LibraryUpdateNotificationMode.Standard
        },
    )

    private var mangaToUpdate: List<LibraryManga> = mutableListOf()

    private var skippedUpdates: List<SkippedUpdate> = emptyList()

    override suspend fun doWork(): Result {
        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val preferences = appGraph.libraryPreferences
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }

            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        if (WORK_NAME_MANUAL in tags) notifier.cancelUpdateSkippedNotification()
        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addMangaToQueue(categoryId)

        return withIOContext {
            try {
                notifier.onUpdateStarted()
                updateChapterList()
                reportSkippedUpdates()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = MangaLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Adds list of manga to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addMangaToQueue(categoryId: Long) {
        val libraryManga = getLibraryManga.await()

        val listToUpdate = if (categoryId != -1L) {
            libraryManga.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = libraryPreferences.mangaUpdateCategories().get().map { it.toLong() }
            val includedManga = if (categoriesToUpdate.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToUpdate }
            } else {
                libraryManga
            }

            val categoriesToExclude = libraryPreferences.mangaUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedMangaIds = if (categoriesToExclude.isNotEmpty()) {
                libraryManga.filter { it.category in categoriesToExclude }.map { it.manga.id }
            } else {
                emptyList()
            }

            includedManga
                .filterNot { it.manga.id in excludedMangaIds }
                .distinctBy { it.manga.id }
        }

        // Smart-update restrictions control background update selection and are independent
        // from library UI-only filters like "customized update frequency".
        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val skipped = mutableListOf<SkippedUpdate>()
        val (_, fetchWindowUpperBound) = mangaFetchInterval.getWindow(ZonedDateTime.now())

        mangaToUpdate = listToUpdate
            .filter {
                val skipReason = evaluateAutoUpdateCandidate(
                    candidate = AutoUpdateCandidate(
                        alwaysUpdate = it.manga.updateStrategy == UpdateStrategy.ALWAYS_UPDATE,
                        isCompleted = it.manga.status.toInt() == SManga.COMPLETED,
                        hasUnviewed = it.unreadCount != 0L,
                        hasStarted = it.hasStarted,
                        totalCount = it.totalChapters,
                        nextUpdate = it.manga.nextUpdate,
                    ),
                    restrictions = restrictions,
                    fetchWindowUpperBound = fetchWindowUpperBound,
                )

                if (skipReason != null) {
                    val source = sourceManager.getOrStub(it.manga.source).toString()
                    skipped.add(SkippedUpdate(skipReason, source, it.manga.title))
                    false
                } else {
                    true
                }
            }
            .sortedBy { it.manga.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(mangaToUpdate)

        skippedUpdates = skipped
        if (skipped.isNotEmpty()) {
            logcat {
                skipped
                    .groupBy { it.reason }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    private fun reportSkippedUpdates() {
        val isManualRun = WORK_NAME_MANUAL in tags
        val skipped = skippedUpdatesForReport(isManualRun = isManualRun, skipped = skippedUpdates)

        val outcome = context.writeSkippedUpdateReport(SKIPPED_LOG_FILENAME, skipped)
        val file = (outcome as? ErrorLogWriteOutcome.Created)?.file?.takeIf(::hasShareableErrorLogFile)
        if (file != null) {
            notifier.showUpdateSkippedNotification(skipped.size, file.getUriCompat(context))
        } else if (outcome is ErrorLogWriteOutcome.Failed) {
            logcat(LogPriority.WARN, outcome.cause) { "Failed to write manga library update skipped file" }
        }
    }

    /**
     * Method that updates manga in [mangaToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each manga it calls [updateManga] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun CoroutineScope.updateChapterList() {
        val semaphore = Semaphore(5)
        val progressTracker = LibraryUpdateProgressTracker(
            total = mangaToUpdate.size,
            title = { it.title },
            onProgress = { updatingManga, progress ->
                notifier.showProgressNotification(
                    updatingManga,
                    progress.completed,
                    progress.total,
                )
                setProgress(progress.toWorkData())
            },
        )
        setProgress(
            LibraryUpdateProgress(
                activeTitles = emptyList(),
                completed = 0,
                total = mangaToUpdate.size,
            ).toWorkData(),
        )
        val newUpdates = CopyOnWriteArrayList<Pair<Manga, Array<Chapter>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Manga, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = mangaFetchInterval.getWindow(ZonedDateTime.now())

        coroutineScope {
            mangaToUpdate.groupBy { it.manga.source }.values
                .map { mangaInSource ->
                    async {
                        semaphore.withPermit {
                            mangaInSource.forEach { libraryManga ->
                                val manga = libraryManga.manga
                                ensureActive()

                                // Don't continue to update if manga is not in library
                                if (getManga.await(manga.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(
                                    progressTracker,
                                    manga,
                                ) {
                                    try {
                                        val newChapters = updateManga(manga, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newChapters.isNotEmpty()) {
                                            val chaptersToDownload = filterChaptersForDownload.await(manga, newChapters)
                                            if (chaptersToDownload.isNotEmpty()) {
                                                downloadChapters(manga, chaptersToDownload)
                                                hasDownloads.set(true)
                                            }
                                            libraryPreferences.newMangaUpdatesCount()
                                                .getAndSet { it + newChapters.size }

                                            // Convert to the manga that contains new chapters
                                            newUpdates.add(manga to newChapters.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoChaptersException -> context.stringResource(
                                                MR.strings.no_chapters_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is SourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        failedUpdates.add(manga to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateSummaryNotification(newUpdates)
            if (notifier.shouldShowUpdateDetailNotifications()) {
                launch(Dispatchers.Main) {
                    notifier.showUpdateDetailNotifications(newUpdates)
                }
            }
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            when (val errorLogOutcome = writeErrorFile(failedUpdates)) {
                is ErrorLogWriteOutcome.Created -> {
                    val shareableErrorFile = errorLogOutcome.file.takeIf(::hasShareableErrorLogFile)
                    if (shareableErrorFile != null) {
                        notifier.showUpdateErrorNotification(
                            failedUpdates.size,
                            shareableErrorFile.getUriCompat(context),
                        )
                    } else {
                        logcat(LogPriority.WARN) {
                            "Manga library update error log file missing; skipping error log notification action"
                        }
                    }
                }
                ErrorLogWriteOutcome.NoErrors -> Unit
                is ErrorLogWriteOutcome.Failed -> {
                    logcat(LogPriority.WARN, errorLogOutcome.cause) {
                        "Failed to write manga library update error file; skipping error log notification action"
                    }
                }
            }
        }
    }

    private fun downloadChapters(manga: Manga, chapters: List<Chapter>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadChapters(manga, chapters, false)
    }

    /**
     * Updates the chapters for the given manga and adds them to the database.
     *
     * @param manga the manga to update.
     * @return a pair of the inserted and removed chapters.
     */
    private suspend fun updateManga(manga: Manga, fetchWindow: Pair<Long, Long>): List<Chapter> {
        val source = sourceManager.getOrStub(manga.source)

        val update = updateMangaFromRemote(
            source = source,
            manga = manga,
            fetchDetails = libraryPreferences.autoUpdateMetadata().get(),
            fetchChapters = true,
            manualFetch = false,
            fetchWindow = fetchWindow,
        )
            .getOrThrow()

        return if (update.manga.favorite) update.newChapters else emptyList()
    }

    private suspend fun withUpdateNotification(
        progressTracker: LibraryUpdateProgressTracker<Manga>,
        manga: Manga,
        block: suspend () -> Unit,
    ) = coroutineScope {
        currentCoroutineContext().ensureActive()
        progressTracker.entryStarted(manga)

        block()

        currentCoroutineContext().ensureActive()
        progressTracker.entryCompleted(manga)
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<Pair<Manga, String?>>): ErrorLogWriteOutcome {
        return writeErrorLogOutcome(hasErrors = errors.isNotEmpty()) {
            val file = context.createFileInCacheDir(ERROR_LOG_FILENAME)
            file.bufferedWriter().use { out ->
                out.write(
                    context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                )
                // Error file format:
                // ! Error
                //   # Source
                //     - Manga
                errors.groupBy({ it.second }, { it.first }).forEach { (error, mangas) ->
                    out.write("\n! ${error}\n")
                    mangas.groupBy { it.source }.forEach { (srcId, mangas) ->
                        val source = sourceManager.getOrStub(srcId)
                        out.write("  # $source\n")
                        mangas.forEach {
                            out.write("    - ${it.title}\n")
                        }
                    }
                }
            }
            file
        }
    }

    companion object {
        internal const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        internal const val ERROR_LOG_FILENAME = "rayniyomi_update_errors.txt"
        private const val SKIPPED_LOG_FILENAME = "rayniyomi_manga_update_skipped.txt"
        private const val ERROR_LOG_HELP_URL = "https://aniyomi.org/docs/guides/troubleshooting/"
        private const val MANGA_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = appGraph.libraryPreferences
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequestBuilder = NetworkRequest.Builder()
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<MangaLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(
            context: Context,
            category: Category? = null,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
            )
            val request = OneTimeWorkRequestBuilder<MangaLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
