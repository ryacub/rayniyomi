package eu.kanade.tachiyomi.data.translation

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages background translation of manga chapter pages.
 *
 * Uses an owned [CoroutineScope] with [SupervisorJob] for structured concurrency.
 * Processes pages sequentially to avoid API rate limits.
 */
class TranslationManager(
    private val context: Context,
    private val translationEngineFactory: TranslationEngineFactory = Injekt.get(),
    private val translationPreferences: TranslationPreferences = Injekt.get(),
    private val translationStorageManager: TranslationStorageManager = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    scope: CoroutineScope? = null,
    private val chapterRunner: TranslationChapterRunner = TranslationChapterRunner(translationStorageManager),
) {

    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _translationStates = MutableStateFlow<Map<Long, TranslationState>>(emptyMap())
    val translationStates: StateFlow<Map<Long, TranslationState>> = _translationStates.asStateFlow()

    private val _chapterTitles = MutableStateFlow<Map<Long, String>>(emptyMap())

    val chapterTitles: StateFlow<Map<Long, String>> = _chapterTitles.asStateFlow()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _languageGeneration = MutableStateFlow(0)

    /**
     * Bumps once the target language change has fully settled. Observers that need to run
     * after stale state is gone should collect this instead of the preference itself.
     */
    val languageGeneration: StateFlow<Int> = _languageGeneration.asStateFlow()

    init {
        this.scope.launch {
            translationPreferences.targetLanguage().changes()
                .distinctUntilChanged()
                .drop(1) // ignore initial emission from changes()
                .collect { onTargetLanguageChanged() }
        }
    }

    /**
     * Joins before clearing so a cancelled job finishing its non-suspending tail
     * cannot repopulate stale old-language state.
     */
    private suspend fun onTargetLanguageChanged() {
        val jobs = activeJobs.values.toList()
        jobs.forEach { it.cancel() }
        activeJobs.clear()
        jobs.forEach { it.join() }
        _translationStates.value = emptyMap()
        _chapterTitles.value = emptyMap()
        _languageGeneration.update { it + 1 }
    }

    /**
     * Start translating a chapter. Each page is processed sequentially.
     */
    fun translateChapter(
        manga: Manga,
        chapter: Chapter,
        source: MangaSource,
    ) {
        val chapterId = chapter.id

        // Don't start if already translating
        if (activeJobs[chapterId]?.isActive == true) return
        _chapterTitles.update { it + (chapterId to "${manga.title} - ${chapter.name}") }

        val engine = translationEngineFactory.create()
        if (engine == null) {
            updateState(
                chapterId,
                TranslationState.Error("No translation model is selected. Choose a model in Settings > Translation."),
            )
            return
        }

        val targetLang = translationPreferences.targetLanguage().get()
        val provider = translationPreferences.translationProvider().get().name

        val job = scope.launch {
            try {
                downloadManager.buildPageList(source, manga, chapter) { pages ->
                    if (pages.isEmpty()) {
                        updateState(chapterId, TranslationState.Error("No pages found"))
                    } else {
                        chapterRunner.run(
                            manga = manga,
                            chapter = chapter,
                            source = source,
                            pages = pages,
                            engine = engine,
                            targetLang = targetLang,
                            provider = provider,
                        ) { state -> updateState(chapterId, state) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Translation failed for chapter ${chapter.name}" }
                updateState(chapterId, TranslationState.Error(e.message ?: "Unknown error"))
            } finally {
                activeJobs.remove(chapterId)
            }
        }

        // Store job immediately after launch to avoid race window
        activeJobs[chapterId] = job
    }

    /**
     * Cancel an in-progress translation.
     */
    fun cancelTranslation(chapterId: Long) {
        activeJobs.remove(chapterId)?.cancel()
        updateState(chapterId, TranslationState.Idle)
    }

    /**
     * Check if a chapter is translated (from storage).
     */
    fun isChapterTranslated(
        chapter: Chapter,
        mangaTitle: String,
        source: MangaSource,
    ): Boolean {
        val targetLang = translationPreferences.targetLanguage().get()
        return translationStorageManager.isChapterTranslated(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            mangaTitle = mangaTitle,
            source = source,
            targetLang = targetLang,
        )
    }

    /**
     * Delete translation for a chapter.
     */
    fun deleteTranslation(
        chapter: Chapter,
        mangaTitle: String,
        source: MangaSource,
    ) {
        val targetLang = translationPreferences.targetLanguage().get()
        translationStorageManager.deleteTranslation(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            mangaTitle = mangaTitle,
            source = source,
            targetLang = targetLang,
        )
        updateState(chapter.id, TranslationState.Idle)
    }

    private fun updateState(chapterId: Long, state: TranslationState) {
        if (state is TranslationState.Idle) {
            _chapterTitles.update { it - chapterId }
        }
        _translationStates.update { current ->
            current.toMutableMap().apply {
                if (state is TranslationState.Idle) {
                    remove(chapterId)
                } else {
                    put(chapterId, state)
                }
            }
        }
    }
}
