package eu.kanade.domain.source.anime.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.source.anime.model.AnimeSource

val AnimeSource.icon: ImageBitmap?
    get() {
        return appGraph.animeExtensionManager.getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
    }
