package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.download.manga.model.DownloadedChapterPage
import eu.kanade.tachiyomi.data.translation.engine.ImageFormatUtil
import eu.kanade.tachiyomi.data.translation.renderer.TranslationRenderer
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
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
    private val telemetry: TranslationRunTelemetry = NoOpTranslationRunTelemetry,
    private val render: (ByteArray, TranslationResult) -> ByteArray = TranslationRenderer::render,
) {

    /**
     * [pages] must be non-empty; the caller reports the empty case.
     * [onState] receives every [TranslationState] for this chapter. A chapter reaches
     * [TranslationState.Translated] only when every source page has a resolved outcome.
     */
    suspend fun run(
        manga: Manga,
        chapter: Chapter,
        source: MangaSource,
        pages: List<DownloadedChapterPage>,
        engine: TranslationEngine,
        targetLang: String,
        provider: String,
        model: String = "",
        onState: (TranslationState) -> Unit,
    ) {
        val startedAtNanos = System.nanoTime()
        var outcomes = pages.indices.associateWith { TranslationPageOutcome.NOT_ATTEMPTED }.toMutableMap()
        var resolvedPages = 0
        var retryCount = 0
        var terminalStatus = TranslationRunStatus.FAILED
        try {
            val initialCoverage = translationStorageManager.getTranslationCoverage(
                chapter.name,
                chapter.scanlator,
                manga.title,
                source,
                targetLang,
            )?.takeIf { it.totalPages == pages.size }
            val legacyResolvedPages = if (initialCoverage == null) {
                pages.mapIndexedNotNullTo(mutableSetOf()) { index, _ ->
                    translationStorageManager.getTranslatedPageFile(
                        chapter.name,
                        chapter.scanlator,
                        manga.title,
                        source,
                        targetLang,
                        index,
                    )?.let { index }
                }
            } else {
                emptySet()
            }
            if (initialCoverage == null && !translationStorageManager.initializeTranslationCoverage(
                    chapter.name,
                    chapter.scanlator,
                    manga.title,
                    source,
                    targetLang,
                    pages.size,
                    legacyResolvedPages,
                )
            ) {
                terminalStatus = TranslationRunStatus.INCOMPLETE
                onState(
                    TranslationState.Incomplete(
                        resolvedPages = 0,
                        totalPages = pages.size,
                        unresolvedPages = pages.indices.map { it + 1 },
                        reason = "Translation coverage could not be saved",
                    ),
                )
                return
            }

            outcomes = pages.indices.associateWith { index ->
                initialCoverage?.outcomes?.get(index)
                    ?: if (index in legacyResolvedPages) {
                        TranslationPageOutcome.LEGACY_STORED
                    } else {
                        TranslationPageOutcome.NOT_ATTEMPTED
                    }
            }.toMutableMap()
            resolvedPages = outcomes.count { it.value.isResolved() }
            var incompleteReason: String? = null
            onState(TranslationState.Translating(resolvedPages, pages.size))

            for ((index, page) in pages.withIndex()) {
                val storedPage = if (outcomes[index]?.isResolved() == true) {
                    translationStorageManager.getTranslatedPageFile(
                        chapter.name,
                        chapter.scanlator,
                        manga.title,
                        source,
                        targetLang,
                        index,
                    )
                } else {
                    null
                }
                if (storedPage != null) {
                    onState(TranslationState.Translating(resolvedPages, pages.size))
                    continue
                }
                if (outcomes[index]?.isResolved() == true) {
                    outcomes[index] = TranslationPageOutcome.NOT_ATTEMPTED
                    resolvedPages--
                }

                val imageBytes = try {
                    page.openStream()?.use { it.readBytes() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (imageBytes == null || imageBytes.isEmpty()) {
                    recordOutcome(
                        chapter,
                        manga,
                        source,
                        targetLang,
                        outcomes,
                        index,
                        TranslationPageOutcome.UNREADABLE_INPUT,
                    )
                    incompleteReason = incompleteReason ?: "Page ${index + 1} could not be read"
                    logOutcome(chapter, index, TranslationPageOutcome.UNREADABLE_INPUT)
                    continue
                }

                val result = try {
                    retryPolicy.execute(
                        label = "chapter \"${chapter.name}\" page ${index + 1}",
                        onRetry = {
                            retryCount++
                            onState(
                                TranslationState.Translating(
                                    currentPage = resolvedPages,
                                    totalPages = pages.size,
                                    phase = TranslationPhase.Retrying(page = index + 1),
                                ),
                            )
                        },
                    ) {
                        engine.detectAndTranslate(imageBytes, targetLang)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val outcome = if (e is InvalidTranslationResponseException) {
                        TranslationPageOutcome.INVALID_PROVIDER_OUTPUT
                    } else {
                        TranslationPageOutcome.PROVIDER_FAILURE
                    }
                    recordOutcome(chapter, manga, source, targetLang, outcomes, index, outcome)
                    incompleteReason = incompleteReason ?: "Page ${index + 1} could not be translated"
                    logOutcome(chapter, index, outcome)
                    break
                }

                val hasTranslatedRegions = result.regions.isNotEmpty()
                val renderedBytes = if (hasTranslatedRegions) {
                    try {
                        render(imageBytes, result)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    imageBytes
                }
                if (renderedBytes == null) {
                    recordOutcome(
                        chapter,
                        manga,
                        source,
                        targetLang,
                        outcomes,
                        index,
                        TranslationPageOutcome.RENDER_FAILURE,
                    )
                    incompleteReason = incompleteReason ?: "Page ${index + 1} could not be rendered"
                    logOutcome(chapter, index, TranslationPageOutcome.RENDER_FAILURE)
                    break
                }

                val extension = ImageFormatUtil.detectExtension(imageBytes)
                val fileName = "%03d.%s".format(index + 1, extension)
                val stored = try {
                    translationStorageManager.writeTranslatedPage(
                        chapterName = chapter.name,
                        chapterScanlator = chapter.scanlator,
                        mangaTitle = manga.title,
                        source = source,
                        targetLang = targetLang,
                        fileName = fileName,
                        imageBytes = renderedBytes,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val outcome = if (stored == null) {
                    TranslationPageOutcome.STORAGE_FAILURE
                } else if (hasTranslatedRegions) {
                    TranslationPageOutcome.TRANSLATED
                } else {
                    TranslationPageOutcome.NO_TEXT
                }

                if (outcome == TranslationPageOutcome.STORAGE_FAILURE) {
                    recordOutcome(chapter, manga, source, targetLang, outcomes, index, outcome)
                    incompleteReason = incompleteReason ?: "Page ${index + 1} could not be stored"
                    logOutcome(chapter, index, outcome)
                    break
                }
                if (!recordOutcome(chapter, manga, source, targetLang, outcomes, index, outcome)) {
                    outcomes[index] = TranslationPageOutcome.STORAGE_FAILURE
                    incompleteReason = incompleteReason ?: "Page ${index + 1} could not be stored"
                    logOutcome(chapter, index, TranslationPageOutcome.STORAGE_FAILURE)
                    break
                }

                resolvedPages++
                onState(TranslationState.Translating(resolvedPages, pages.size))
            }

            val unresolvedPages = pages.indices
                .filterNot { outcomes[it]?.isResolved() == true }
                .map { it + 1 }
            if (unresolvedPages.isEmpty()) {
                translationStorageManager.writeMetadata(
                    chapterName = chapter.name,
                    chapterScanlator = chapter.scanlator,
                    mangaTitle = manga.title,
                    source = source,
                    targetLang = targetLang,
                    provider = provider,
                )
                onState(TranslationState.Translated)
                terminalStatus = TranslationRunStatus.TRANSLATED
            } else {
                onState(
                    TranslationState.Incomplete(
                        resolvedPages = pages.size - unresolvedPages.size,
                        totalPages = pages.size,
                        unresolvedPages = unresolvedPages,
                        reason = incompleteReason ?: "Translation incomplete on page ${unresolvedPages.first()}",
                    ),
                )
                terminalStatus = TranslationRunStatus.INCOMPLETE
            }
        } catch (e: CancellationException) {
            terminalStatus = TranslationRunStatus.CANCELLED
            throw e
        } catch (e: Exception) {
            terminalStatus = TranslationRunStatus.FAILED
            throw e
        } finally {
            val durationMs = ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
            runCatching {
                telemetry.record(
                    TranslationRunEvent(
                        provider = provider,
                        model = model,
                        targetLanguage = targetLang,
                        totalPages = pages.size,
                        resolvedPages = resolvedPages,
                        retryCount = retryCount,
                        durationMs = durationMs,
                        terminalStatus = terminalStatus,
                        outcomeCounts = outcomes.values.groupingBy { it }.eachCount(),
                    ),
                )
            }.onFailure { error ->
                logcat(LogPriority.WARN, error) { "Translation telemetry failed" }
            }
        }
    }

    private fun recordOutcome(
        chapter: Chapter,
        manga: Manga,
        source: MangaSource,
        targetLang: String,
        outcomes: MutableMap<Int, TranslationPageOutcome>,
        pageIndex: Int,
        outcome: TranslationPageOutcome,
    ): Boolean {
        outcomes[pageIndex] = outcome
        return translationStorageManager.writePageOutcome(
            chapter.name,
            chapter.scanlator,
            manga.title,
            source,
            targetLang,
            pageIndex,
            outcome,
        )
    }

    private fun logOutcome(chapter: Chapter, pageIndex: Int, outcome: TranslationPageOutcome) {
        logcat(LogPriority.WARN) {
            "Translation page outcome: chapter \"${chapter.name}\", page ${pageIndex + 1}, outcome $outcome"
        }
    }
}
