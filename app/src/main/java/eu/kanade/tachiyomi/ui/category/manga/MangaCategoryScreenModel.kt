package eu.kanade.tachiyomi.ui.category.manga

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.manga.interactor.CreateMangaCategoryWithName
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.HideMangaCategory
import tachiyomi.domain.category.manga.interactor.RenameMangaCategory
import tachiyomi.domain.category.manga.interactor.ReorderMangaCategory
import tachiyomi.domain.category.manga.interactor.SetMangaCategoryAlphabeticalSort
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.isAlphabeticalCategorySortEnabled
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCategoryScreenModel(
    private val getAllCategories: GetMangaCategories = Injekt.get(),
    private val getVisibleCategories: GetVisibleMangaCategories = Injekt.get(),
    private val createCategoryWithName: CreateMangaCategoryWithName = Injekt.get(),
    private val hideCategory: HideMangaCategory = Injekt.get(),
    private val deleteCategory: DeleteMangaCategory = Injekt.get(),
    private val reorderCategory: ReorderMangaCategory = Injekt.get(),
    private val renameCategory: RenameMangaCategory = Injekt.get(),
    private val setAlphabeticalSortInteractor: SetMangaCategoryAlphabeticalSort = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) : StateScreenModel<MangaCategoryScreenState>(MangaCategoryScreenState.Loading) {

    private val _events: Channel<MangaCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            val allCategories = if (libraryPreferences.hideHiddenCategoriesSettings().get()) {
                getVisibleCategories.subscribe()
            } else {
                getAllCategories.subscribe()
            }

            allCategories.collectLatest { categories ->
                val userCategories = categories.filterNot(Category::isSystemCategory)
                mutableState.update {
                    MangaCategoryScreenState.Success(
                        categories = userCategories.toImmutableList(),
                        alphabeticalSortEnabled = userCategories.isAlphabeticalCategorySortEnabled(),
                        parentCategories = userCategories
                            .filter { it.parentId == null }
                            .toImmutableList(),
                    )
                }
            }
        }
    }

    fun createCategory(name: String, parentId: Long?) {
        screenModelScope.launch {
            when (createCategoryWithName.await(name, parentId)) {
                is CreateMangaCategoryWithName.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            when (hideCategory.await(category)) {
                is HideMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        val currentState = state.value as? MangaCategoryScreenState.Success ?: return
        if (currentState.alphabeticalSortEnabled) return
        screenModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameMangaCategory.Result.InternalError -> _events.send(
                    MangaCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun setAlphabeticalSort(enabled: Boolean) {
        screenModelScope.launch {
            setAlphabeticalSortInteractor.await(enabled)
        }
    }

    fun showDialog(dialog: MangaCategoryDialog) {
        mutableState.update {
            when (it) {
                MangaCategoryScreenState.Loading -> it
                is MangaCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                MangaCategoryScreenState.Loading -> it
                is MangaCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface MangaCategoryDialog {
    data object Create : MangaCategoryDialog
    data class Rename(val category: Category) : MangaCategoryDialog
    data class Delete(val category: Category) : MangaCategoryDialog
}

sealed interface MangaCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : MangaCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface MangaCategoryScreenState {

    @Immutable
    data object Loading : MangaCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val alphabeticalSortEnabled: Boolean,
        val parentCategories: ImmutableList<Category>,
        val dialog: MangaCategoryDialog? = null,
    ) : MangaCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
