package eu.kanade.tachiyomi.ui.category.anime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.presentation.util.StateViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.interactor.SetAnimeCategoryAlphabeticalSort
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.isAlphabeticalCategorySortEnabled
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
class AnimeCategoryScreenModel(
    private val getAllCategories: GetAnimeCategories,
    private val getVisibleCategories: GetVisibleAnimeCategories,
    private val createCategoryWithName: CreateAnimeCategoryWithName,
    private val hideCategory: HideAnimeCategory,
    private val deleteCategory: DeleteAnimeCategory,
    private val reorderCategory: ReorderAnimeCategory,
    private val renameCategory: RenameAnimeCategory,
    private val setAlphabeticalSortInteractor: SetAnimeCategoryAlphabeticalSort,
    private val libraryPreferences: LibraryPreferences,
) : StateViewModel<AnimeCategoryScreenState>(AnimeCategoryScreenState.Loading) {

    private val _events: Channel<AnimeCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val allCategories = if (libraryPreferences.hideHiddenCategoriesSettings().get()) {
                getVisibleCategories.subscribe()
            } else {
                getAllCategories.subscribe()
            }

            allCategories.collectLatest { categories ->
                val userCategories = categories.filterNot(Category::isSystemCategory)
                mutableState.update {
                    AnimeCategoryScreenState.Success(
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
        viewModelScope.launch {
            when (createCategoryWithName.await(name, parentId)) {
                CreateAnimeCategoryWithName.Result.InvalidParent -> _events.send(
                    AnimeCategoryEvent.InvalidParentCategory,
                )
                is CreateAnimeCategoryWithName.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        viewModelScope.launch {
            when (hideCategory.await(category)) {
                is HideAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        val currentState = state.value as? AnimeCategoryScreenState.Success ?: return
        if (currentState.alphabeticalSortEnabled) return
        viewModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        viewModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun setAlphabeticalSort(enabled: Boolean) {
        viewModelScope.launch {
            setAlphabeticalSortInteractor.await(enabled)
        }
    }

    fun showDialog(dialog: AnimeCategoryDialog) {
        mutableState.update {
            when (it) {
                AnimeCategoryScreenState.Loading -> it
                is AnimeCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                AnimeCategoryScreenState.Loading -> it
                is AnimeCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface AnimeCategoryDialog {
    data object Create : AnimeCategoryDialog
    data class Rename(val category: Category) : AnimeCategoryDialog
    data class Delete(val category: Category) : AnimeCategoryDialog
}

sealed interface AnimeCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeCategoryEvent
    data object InvalidParentCategory : LocalizedMessage(MR.strings.error_invalid_parent_category)
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface AnimeCategoryScreenState {

    @Immutable
    data object Loading : AnimeCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val alphabeticalSortEnabled: Boolean,
        val parentCategories: ImmutableList<Category>,
        val dialog: AnimeCategoryDialog? = null,
    ) : AnimeCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
