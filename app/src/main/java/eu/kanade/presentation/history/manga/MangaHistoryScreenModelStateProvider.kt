package eu.kanade.presentation.history.manga

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryScreenModel
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import java.time.LocalDate
import java.util.Date

class MangaHistoryScreenModelStateProvider : PreviewParameterProvider<MangaHistoryScreenModel.State> {

    private val multiPage = MangaHistoryScreenModel.State(
        searchQuery = null,
        list =
        listOf(HistoryUiModelExamples.headerToday)
            .asSequence()
            .plus(HistoryUiModelExamples.items(start = 1).take(3))
            .plus(HistoryUiModelExamples.header(daysAgo = 1))
            .plus(HistoryUiModelExamples.items(start = 4).take(1))
            .plus(HistoryUiModelExamples.header(daysAgo = 2))
            .plus(HistoryUiModelExamples.items(start = 5).take(7))
            .toList(),
        dialog = null,
    )

    private val shortRecent = MangaHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items(start = 1).first(),
        ),
        dialog = null,
    )

    private val shortFuture = MangaHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items(start = 1).first(),
        ),
        dialog = null,
    )

    private val empty = MangaHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = MangaHistoryScreenModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = MangaHistoryScreenModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<MangaHistoryScreenModel.State> = sequenceOf(
        multiPage,
        shortRecent,
        shortFuture,
        empty,
        loadingWithSearchQuery,
        loading,
    )

    private object HistoryUiModelExamples {
        private val previewToday = LocalDate.of(2024, 1, 15)

        val headerToday = header()
        val headerTomorrow = MangaHistoryUiModel.Header(previewToday.plusDays(1))

        fun header(daysAgo: Long = 0) = MangaHistoryUiModel.Header(previewToday.minusDays(daysAgo))

        fun items(start: Int) = generateSequence(start) { it + 1 }.map(::item)

        private fun item(n: Int) = MangaHistoryUiModel.Item(
            MangaHistoryWithRelations(
                id = n.toLong(),
                chapterId = n.toLong(),
                mangaId = n.toLong(),
                title = "Example Title $n",
                chapterNumber = n.toDouble(),
                readAt = Date(1_705_300_000_000L),
                readDuration = n * 60_000L,
                coverData = MangaCover(
                    mangaId = n.toLong(),
                    sourceId = 1L,
                    isMangaFavorite = false,
                    url = "https://example.com/cover.png",
                    lastModified = 0L,
                ),
            ),
        )
    }
}
