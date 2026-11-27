package eu.kanade.presentation.history.anime

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.history.anime.model.AnimeHistoryWithRelations
import java.time.LocalDate
import java.util.Date

class AnimeHistoryScreenModelStateProvider : PreviewParameterProvider<AnimeHistoryScreenModel.State> {

    private val multiPage = AnimeHistoryScreenModel.State(
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

    private val shortRecent = AnimeHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerToday,
            HistoryUiModelExamples.items(start = 1).first(),
        ),
        dialog = null,
    )

    private val shortFuture = AnimeHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(
            HistoryUiModelExamples.headerTomorrow,
            HistoryUiModelExamples.items(start = 1).first(),
        ),
        dialog = null,
    )

    private val empty = AnimeHistoryScreenModel.State(
        searchQuery = null,
        list = listOf(),
        dialog = null,
    )

    private val loadingWithSearchQuery = AnimeHistoryScreenModel.State(
        searchQuery = "Example Search Query",
    )

    private val loading = AnimeHistoryScreenModel.State(
        searchQuery = null,
        list = null,
        dialog = null,
    )

    override val values: Sequence<AnimeHistoryScreenModel.State> = sequenceOf(
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
        val headerTomorrow = AnimeHistoryUiModel.Header(previewToday.plusDays(1))

        fun header(daysAgo: Long = 0) = AnimeHistoryUiModel.Header(previewToday.minusDays(daysAgo))

        fun items(start: Int) = generateSequence(start) { it + 1 }.map(::item)

        private fun item(n: Int) = AnimeHistoryUiModel.Item(
            AnimeHistoryWithRelations(
                id = n.toLong(),
                episodeId = n.toLong(),
                animeId = n.toLong(),
                title = "Example Title $n",
                episodeNumber = n.toDouble(),
                seenAt = Date(1_705_300_000_000L),
                coverData = AnimeCover(
                    animeId = n.toLong(),
                    sourceId = 1L,
                    isAnimeFavorite = false,
                    url = "https://example.com/cover.png",
                    lastModified = 0L,
                ),
            ),
        )
    }
}
