package eu.kanade.tachiyomi.data.download.manga.model

import java.io.InputStream

/**
 * A single page of a downloaded chapter.
 *
 * Content is read through [openStream]. Loose-file chapters expose the image
 * through its content URI, archived chapters expose it directly from the
 * archive stream. An archive-backed stream is valid only inside the
 * `buildPageList` block, so consumers must read pages within that block.
 */
class DownloadedChapterPage(
    val index: Int,
    private val streamOpener: () -> InputStream?,
) {
    fun openStream(): InputStream? = streamOpener()
}
