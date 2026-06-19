package tachiyomi.domain.category.anime.interactor

import tachiyomi.domain.category.interactor.SetCategoryDisplayMode
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences

class SetAnimeDisplayMode(
    private val preferences: LibraryPreferences,
) {

    private val setCategoryDisplayMode = SetCategoryDisplayMode(preferences)

    fun await(display: LibraryDisplayMode) {
        setCategoryDisplayMode.await(display)
    }
}
