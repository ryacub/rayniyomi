package mihon.feature.upcoming.manga

import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.core.util.insertSeparatorsReversed
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate
import java.time.YearMonth

class UpcomingMangaScreenModel(
    private val getUpcomingManga: GetUpcomingManga = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<UpcomingMangaScreenModel.State>(State()) {

    val categories: StateFlow<List<Category>> = getCategories.subscribe()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val includedCategoriesPref = libraryPreferences.filterMangaUpcomingCategories()
    private val excludedCategoriesPref = libraryPreferences.filterMangaUpcomingCategoriesExclude()

    val includedCategories by includedCategoriesPref.asState(screenModelScope)
    val excludedCategories by excludedCategoriesPref.asState(screenModelScope)

    init {
        screenModelScope.launch {
            categoryFilterFlow().collectLatest {
                mutableState.update { state ->
                    val upcomingItems = it.toUpcomingMangaUIModels()
                    state.copy(
                        items = upcomingItems,
                        events = upcomingItems.toEvents(),
                        headerIndexes = upcomingItems.getHeaderIndexes(),
                    )
                }
            }
        }
    }

    private fun categoryFilterFlow(): Flow<List<Manga>> {
        return combine(
            includedCategoriesPref.changes(),
            excludedCategoriesPref.changes(),
        ) { included, excluded -> included to excluded }
            .distinctUntilChanged()
            .flatMapLatest { (included, excluded) ->
                getUpcomingManga.subscribe(
                    included.mapNotNull { it.toLongOrNull() },
                    excluded.mapNotNull { it.toLongOrNull() },
                )
            }
    }

    fun cycleCategory(category: Category) {
        val categoryId = category.id.toString()
        when (categoryId) {
            in includedCategoriesPref.get() -> {
                includedCategoriesPref.getAndSet { it - categoryId }
                excludedCategoriesPref.getAndSet { it + categoryId }
            }
            in excludedCategoriesPref.get() -> excludedCategoriesPref.getAndSet { it - categoryId }
            else -> includedCategoriesPref.getAndSet { it + categoryId }
        }
    }

    private fun List<Manga>.toUpcomingMangaUIModels(): ImmutableList<UpcomingMangaUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingMangaUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.manga?.expectedNextUpdate?.toLocalDate()

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingMangaUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
            .toImmutableList()
    }

    private fun List<UpcomingMangaUIModel>.toEvents(): ImmutableMap<LocalDate, Int> {
        return filterIsInstance<UpcomingMangaUIModel.Header>()
            .associate { it.date to it.mangaCount }
            .toImmutableMap()
    }

    private fun List<UpcomingMangaUIModel>.getHeaderIndexes(): ImmutableMap<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingMangaUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
            .toImmutableMap()
    }

    fun setSelectedYearMonth(yearMonth: YearMonth) {
        mutableState.update { it.copy(selectedYearMonth = yearMonth) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val items: ImmutableList<UpcomingMangaUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data object Filter : Dialog
    }
}
