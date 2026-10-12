package tachiyomi.presentation.widget.util

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.EntryCover
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.components.manga.CoverHeight
import tachiyomi.presentation.widget.components.manga.CoverWidth

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T, C, R> Flow<List<T>>.decodeVisibleCovers(
    limit: Int,
    entryId: (T) -> Long,
    cover: (T) -> C,
    decode: suspend (C) -> R,
): Flow<ImmutableList<Pair<Long, R>>> {
    return map { rows -> rows.distinctBy(entryId).take(limit).map { entryId(it) to cover(it) } }
        .distinctUntilChanged()
        .mapLatest { visible -> visible.map { (id, cover) -> id to decode(cover) }.toImmutableList() }
}

internal suspend fun Context.decodeWidgetCover(cover: EntryCover): Bitmap? = withIOContext {
    val request = ImageRequest.Builder(this@decodeWidgetCover)
        .data(cover)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .precision(Precision.EXACT)
        .size(CoverWidth.value.toInt().dpToPx, CoverHeight.value.toInt().dpToPx)
        .scale(Scale.FILL)
        .let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                it.transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.appwidget_inner_radius)))
            } else {
                it // Handled by system
            }
        }
        .build()
    imageLoader.execute(request).image?.asDrawable(resources)?.toBitmap()
}
