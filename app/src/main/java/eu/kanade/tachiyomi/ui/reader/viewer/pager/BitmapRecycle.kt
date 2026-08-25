package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap

/** Recycles the bitmap unless it is null or already recycled. */
internal fun Bitmap?.recycleIfNeeded() {
    if (this != null && !isRecycled) recycle()
}
