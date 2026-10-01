package eu.kanade.domain

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.domain.update.UpdatePromptPreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.custombutton.CustomButtonRepositoryImpl
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService

@BindingContainer
object DomainModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideReleaseService(networkService: NetworkHelper, json: Json): ReleaseService =
        ReleaseServiceImpl(networkService = networkService, json = json)

    @Provides
    fun provideGetApplicationRelease(service: ReleaseService, preferenceStore: PreferenceStore): GetApplicationRelease =
        GetApplicationRelease(service = service, preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUpdatePromptPreferences(preferenceStore: PreferenceStore): UpdatePromptPreferences =
        UpdatePromptPreferences(preferenceStore = preferenceStore)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUpdatePromptGatekeeper(prefs: UpdatePromptPreferences): UpdatePromptGatekeeper =
        UpdatePromptGatekeeper(prefs = prefs)

    @Provides
    @SingleIn(AppScope::class)
    fun provideCustomButtonRepository(handler: AnimeDatabaseHandler): CustomButtonRepository =
        CustomButtonRepositoryImpl(handler = handler)

    @Provides
    fun provideCreateCustomButton(customButtonRepository: CustomButtonRepository): CreateCustomButton =
        CreateCustomButton(customButtonRepository = customButtonRepository)

    @Provides
    fun provideDeleteCustomButton(customButtonRepository: CustomButtonRepository): DeleteCustomButton =
        DeleteCustomButton(customButtonRepository = customButtonRepository)

    @Provides
    fun provideGetCustomButtons(customButtonRepository: CustomButtonRepository): GetCustomButtons =
        GetCustomButtons(customButtonRepository = customButtonRepository)

    @Provides
    fun provideUpdateCustomButton(customButtonRepository: CustomButtonRepository): UpdateCustomButton =
        UpdateCustomButton(customButtonRepository = customButtonRepository)

    @Provides
    fun provideReorderCustomButton(customButtonRepository: CustomButtonRepository): ReorderCustomButton =
        ReorderCustomButton(customButtonRepository = customButtonRepository)

    @Provides
    fun provideToggleFavoriteCustomButton(customButtonRepository: CustomButtonRepository): ToggleFavoriteCustomButton =
        ToggleFavoriteCustomButton(customButtonRepository = customButtonRepository)
}
