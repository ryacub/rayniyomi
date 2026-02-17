package eu.kanade.tachiyomi.di

import android.app.Application
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.tachiyomi.feature.novel.LightNovelFeatureGate
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginReadiness
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class NovelFeatureModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory { LightNovelPluginManager(app, get(), get()) }
        addSingletonFactory<LightNovelPluginReadiness> { get<LightNovelPluginManager>() }
        addSingletonFactory { LightNovelFeatureGate(get<NovelFeaturePreferences>(), get()) }
    }
}
