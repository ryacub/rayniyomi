package eu.kanade.domain.source.manga.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.source.manga.model.Source

val Source.icon: ImageBitmap?
    get() {
        return appGraph.mangaExtensionManager.getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
    }
