package eu.kanade.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl
import tachiyomi.data.category.manga.MangaCategoryRepositoryImpl
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.anime.interactor.ResetAnimeCategoryFlags
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategoryAlphabeticalSort
import tachiyomi.domain.category.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.category.anime.interactor.SetSortModeForAnimeCategory
import tachiyomi.domain.category.anime.interactor.UpdateAnimeCategory
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.category.manga.interactor.CreateMangaCategoryWithName
import tachiyomi.domain.category.manga.interactor.DeleteMangaCategory
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.category.manga.interactor.HideMangaCategory
import tachiyomi.domain.category.manga.interactor.RenameMangaCategory
import tachiyomi.domain.category.manga.interactor.ReorderMangaCategory
import tachiyomi.domain.category.manga.interactor.ResetMangaCategoryFlags
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategoryAlphabeticalSort
import tachiyomi.domain.category.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.category.manga.interactor.SetSortModeForMangaCategory
import tachiyomi.domain.category.manga.interactor.UpdateMangaCategory
import tachiyomi.domain.category.manga.repository.MangaCategoryRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.service.LibraryPreferences

@BindingContainer
object CategoryBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnimeCategoryRepository(handler: AnimeDatabaseHandler): AnimeCategoryRepository =
        AnimeCategoryRepositoryImpl(handler = handler)

    @Provides
    fun provideGetAnimeCategories(categoryRepository: AnimeCategoryRepository): GetAnimeCategories =
        GetAnimeCategories(categoryRepository = categoryRepository)

    @Provides
    fun provideGetVisibleAnimeCategories(categoryRepository: AnimeCategoryRepository): GetVisibleAnimeCategories =
        GetVisibleAnimeCategories(categoryRepository = categoryRepository)

    @Provides
    fun provideResetAnimeCategoryFlags(
        preferences: LibraryPreferences,
        categoryRepository: AnimeCategoryRepository,
    ): ResetAnimeCategoryFlags =
        ResetAnimeCategoryFlags(preferences = preferences, categoryRepository = categoryRepository)

    @Provides
    fun provideSetAnimeDisplayMode(preferences: LibraryPreferences): SetAnimeDisplayMode =
        SetAnimeDisplayMode(preferences = preferences)

    @Provides
    fun provideSetSortModeForAnimeCategory(
        preferences: LibraryPreferences,
        categoryRepository: AnimeCategoryRepository,
    ): SetSortModeForAnimeCategory =
        SetSortModeForAnimeCategory(preferences = preferences, categoryRepository = categoryRepository)

    @Provides
    fun provideSetAnimeCategoryAlphabeticalSort(
        categoryRepository: AnimeCategoryRepository,
    ): SetAnimeCategoryAlphabeticalSort =
        SetAnimeCategoryAlphabeticalSort(categoryRepository = categoryRepository)

    @Provides
    fun provideCreateAnimeCategoryWithName(
        categoryRepository: AnimeCategoryRepository,
        preferences: LibraryPreferences,
    ): CreateAnimeCategoryWithName =
        CreateAnimeCategoryWithName(categoryRepository = categoryRepository, preferences = preferences)

    @Provides
    fun provideRenameAnimeCategory(categoryRepository: AnimeCategoryRepository): RenameAnimeCategory =
        RenameAnimeCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideReorderAnimeCategory(categoryRepository: AnimeCategoryRepository): ReorderAnimeCategory =
        ReorderAnimeCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideUpdateAnimeCategory(categoryRepository: AnimeCategoryRepository): UpdateAnimeCategory =
        UpdateAnimeCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideHideAnimeCategory(categoryRepository: AnimeCategoryRepository): HideAnimeCategory =
        HideAnimeCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideDeleteAnimeCategory(
        categoryRepository: AnimeCategoryRepository,
        libraryPreferences: LibraryPreferences,
        downloadPreferences: DownloadPreferences,
    ): DeleteAnimeCategory =
        DeleteAnimeCategory(
            categoryRepository = categoryRepository,
            libraryPreferences = libraryPreferences,
            downloadPreferences = downloadPreferences,
        )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMangaCategoryRepository(handler: MangaDatabaseHandler): MangaCategoryRepository =
        MangaCategoryRepositoryImpl(handler = handler)

    @Provides
    fun provideGetMangaCategories(categoryRepository: MangaCategoryRepository): GetMangaCategories =
        GetMangaCategories(categoryRepository = categoryRepository)

    @Provides
    fun provideGetVisibleMangaCategories(categoryRepository: MangaCategoryRepository): GetVisibleMangaCategories =
        GetVisibleMangaCategories(categoryRepository = categoryRepository)

    @Provides
    fun provideResetMangaCategoryFlags(
        preferences: LibraryPreferences,
        categoryRepository: MangaCategoryRepository,
    ): ResetMangaCategoryFlags =
        ResetMangaCategoryFlags(preferences = preferences, categoryRepository = categoryRepository)

    @Provides
    fun provideSetMangaDisplayMode(preferences: LibraryPreferences): SetMangaDisplayMode =
        SetMangaDisplayMode(preferences = preferences)

    @Provides
    fun provideSetSortModeForMangaCategory(
        preferences: LibraryPreferences,
        categoryRepository: MangaCategoryRepository,
    ): SetSortModeForMangaCategory =
        SetSortModeForMangaCategory(preferences = preferences, categoryRepository = categoryRepository)

    @Provides
    fun provideSetMangaCategoryAlphabeticalSort(
        categoryRepository: MangaCategoryRepository,
    ): SetMangaCategoryAlphabeticalSort =
        SetMangaCategoryAlphabeticalSort(categoryRepository = categoryRepository)

    @Provides
    fun provideCreateMangaCategoryWithName(
        categoryRepository: MangaCategoryRepository,
        preferences: LibraryPreferences,
    ): CreateMangaCategoryWithName =
        CreateMangaCategoryWithName(categoryRepository = categoryRepository, preferences = preferences)

    @Provides
    fun provideRenameMangaCategory(categoryRepository: MangaCategoryRepository): RenameMangaCategory =
        RenameMangaCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideReorderMangaCategory(categoryRepository: MangaCategoryRepository): ReorderMangaCategory =
        ReorderMangaCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideUpdateMangaCategory(categoryRepository: MangaCategoryRepository): UpdateMangaCategory =
        UpdateMangaCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideHideMangaCategory(categoryRepository: MangaCategoryRepository): HideMangaCategory =
        HideMangaCategory(categoryRepository = categoryRepository)

    @Provides
    fun provideDeleteMangaCategory(
        categoryRepository: MangaCategoryRepository,
        libraryPreferences: LibraryPreferences,
        downloadPreferences: DownloadPreferences,
    ): DeleteMangaCategory =
        DeleteMangaCategory(
            categoryRepository = categoryRepository,
            libraryPreferences = libraryPreferences,
            downloadPreferences = downloadPreferences,
        )

    @Provides
    fun provideSetAnimeCategories(animeRepository: AnimeRepository): SetAnimeCategories =
        SetAnimeCategories(animeRepository = animeRepository)

    @Provides
    fun provideSetMangaCategories(mangaRepository: MangaRepository): SetMangaCategories =
        SetMangaCategories(mangaRepository = mangaRepository)
}
