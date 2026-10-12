package eu.kanade.domain.source.anime.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.source.anime.model.AnimeSource

val AnimeSource.icon: Drawable?
    get() = appGraph.animeExtensionManager.getAppIconForSource(id)
