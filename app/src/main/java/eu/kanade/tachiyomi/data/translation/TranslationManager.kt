package eu.kanade.tachiyomi.data.translation

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.translation.engine.ImageFormatUtil
import eu.kanade.tachiyomi.data.translation.renderer.TranslationRenderer
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {

    private val scope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _translationStates = MutableStateFlow<Map<Long, TranslationState>>(emptyMap())
    val translationStates: StateFlow<Map<Long, TranslationState>> = _translationStates.asStateFlow()

    private val activeJobs = ConcurrentHashMap<Long, Job>()

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

        val engine = translationEngineFactory.create()
        if (engine == null) {
            updateState(chapterId, TranslationState.Error("No translation provider configured"))
            return
        }

        val targetLang = translationPreferences.targetLanguage().get()
        val provider = translationPreferences.translationProvider().get().name

        val job = scope.launch {
            try {
                val pages = downloadManager.buildPageList(source, manga, chapter)
                if (pages.isEmpty()) {
                    updateState(chapterId, TranslationState.Error("No pages found"))
                    return@launch
                }

                updateState(chapterId, TranslationState.Translating(0, pages.size))

                for ((index, page) in pages.withIndex()) {
                    val uri = page.uri ?: continue
                    val imageBytes = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: continue

                    // Call LLM to detect and translate text
                    val result = engine.detectAndTranslate(imageBytes, targetLang)

                    // Render overlay
                    val renderedBytes = if (result.regions.isNotEmpty()) {
                        TranslationRenderer.render(imageBytes, result)
                    } else {
                        imageBytes // No text found, store original
                    }

                    // Determine filename from original page
                    val extension = ImageFormatUtil.detectExtension(imageBytes)
                    val fileName = "%03d.%s".format(index + 1, extension)

                    // Write translated page
                    translationStorageManager.writeTranslatedPage(
                        chapterName = chapter.name,
                        chapterScanlator = chapter.scanlator,
                        mangaTitle = manga.title,
                        source = source,
                        targetLang = targetLang,
                        fileName = fileName,
                        imageBytes = renderedBytes,
                    )

                    // Update progress after processing each page
                    updateState(chapterId, TranslationState.Translating(index + 1, pages.size))
                }

                // Write metadata
                translationStorageManager.writeMetadata(
                    chapterName = chapter.name,
                    chapterScanlator = chapter.scanlator,
                    mangaTitle = manga.title,
                    source = source,
                    targetLang = targetLang,
                    provider = provider,
                )

                updateState(chapterId, TranslationState.Translated)
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
     * Get the current translation state for a chapter.
     */
    fun getState(chapterId: Long): TranslationState {
        return _translationStates.value[chapterId] ?: TranslationState.Idle
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
