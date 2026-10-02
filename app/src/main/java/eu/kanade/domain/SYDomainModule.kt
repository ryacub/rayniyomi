package eu.kanade.domain

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import eu.kanade.domain.source.manga.interactor.ToggleExcludeFromMangaDataSaver
import eu.kanade.domain.source.service.SourcePreferences

@BindingContainer
object SYDomainModule {

    @Provides
    fun provideToggleExcludeFromMangaDataSaver(preferences: SourcePreferences): ToggleExcludeFromMangaDataSaver =
        ToggleExcludeFromMangaDataSaver(preferences = preferences)
}
