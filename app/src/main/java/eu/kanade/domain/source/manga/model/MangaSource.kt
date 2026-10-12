package eu.kanade.domain.source.manga.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.source.manga.model.Source

val Source.icon: Drawable?
    get() = appGraph.mangaExtensionManager.getAppIconForSource(id)
