package eu.kanade.tachiyomi.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationCatalogResult
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
annotation class ViewModelIoDispatcher

@Qualifier
annotation class ViewModelSearchDispatcher

@Qualifier
annotation class TranslationCatalogLoader

@BindingContainer
object ViewModelBindings {

    @Provides
    @ViewModelIoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @ViewModelSearchDispatcher
    fun provideSearchDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(5)

    @Provides
    @TranslationCatalogLoader
    fun provideTranslationCatalogLoader(
        repository: TranslationModelCatalogRepository,
    ): suspend (TranslationProvider, String, Boolean) -> TranslationCatalogResult = repository::load
}
