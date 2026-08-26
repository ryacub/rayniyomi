package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.download.manga.model.DownloadedChapterPage
import eu.kanade.tachiyomi.data.translation.engine.ImageFormatUtil
import eu.kanade.tachiyomi.data.translation.renderer.TranslationRenderer
import eu.kanade.tachiyomi.source.MangaSource
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Translates every page of one chapter in order and reports progress.
 *
 * The retry rationale lives in [TranslationRetryPolicy]: an attempt that fails transiently emits
 * a `Translating` state with phase [TranslationPhase.Retrying] before its backoff, and the phase
 * clears at the progress update after the retried call returns. An extra emission in between is
 * not needed: render and write are fast local operations.
 *
 * [render] is injectable because the real renderer decodes bitmaps and cannot run on the JVM.
 */
class TranslationChapterRunner(
    private val translationStorageManager: TranslationStorageManager,
    private val retryPolicy: TranslationRetryPolicy = TranslationRetryPolicy(),
    private val render: (ByteArray, TranslationResult) -> ByteArray = TranslationRenderer::render,
) {

    /**
     * [pages] must be non-empty; the caller reports the empty case.
     * [onState] receives every [TranslationState] for this chapter, ending in
     * [TranslationState.Translated] on success. Throws on terminal failure; the manager keeps the
     * catch that maps it to [TranslationState.Error].
     */
    suspend fun run(
        manga: Manga,
        chapter: Chapter,
        source: MangaSource,
        pages: List<DownloadedChapterPage>,
        engine: TranslationEngine,
        targetLang: String,
        provider: String,
        onState: (TranslationState) -> Unit,
    ) {
        onState(TranslationState.Translating(0, pages.size))

        for ((index, page) in pages.withIndex()) {
            // Skip pages already written by an earlier run, including stored originals.
            val existing = translationStorageManager.getTranslatedPageFile(
                chapter.name,
                chapter.scanlator,
                manga.title,
                source,
                targetLang,
                index,
            )
            if (existing != null) {
                onState(TranslationState.Translating(index + 1, pages.size))
                continue
            }

            val imageBytes = page.openStream()?.use { it.readBytes() } ?: continue

            val result = retryPolicy.execute(
                label = "chapter \"${chapter.name}\" page ${index + 1}",
                onRetry = {
                    onState(
                        TranslationState.Translating(
                            currentPage = index,
                            totalPages = pages.size,
                            phase = TranslationPhase.Retrying(page = index + 1),
                        ),
                    )
                },
            ) {
                engine.detectAndTranslate(imageBytes, targetLang)
            }

            val renderedBytes = if (result.regions.isNotEmpty()) {
                render(imageBytes, result)
            } else {
                imageBytes // No text found, store original
            }

            val extension = ImageFormatUtil.detectExtension(imageBytes)
            val fileName = "%03d.%s".format(index + 1, extension)

            translationStorageManager.writeTranslatedPage(
                chapterName = chapter.name,
                chapterScanlator = chapter.scanlator,
                mangaTitle = manga.title,
                source = source,
                targetLang = targetLang,
                fileName = fileName,
                imageBytes = renderedBytes,
            )

            onState(TranslationState.Translating(index + 1, pages.size))
        }

        translationStorageManager.writeMetadata(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            mangaTitle = manga.title,
            source = source,
            targetLang = targetLang,
            provider = provider,
        )

        onState(TranslationState.Translated)
    }
}
