package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

internal fun readerPageAnalysisKey(
    page: ReaderPage,
    config: ViewerConfig,
    vararg viewerFlags: Pair<String, Boolean>,
): String = buildString {
    append(page.imageUrl ?: page.url.ifEmpty { "chapter=${page.chapter.chapter.id}|page=${page.index}" })
    append("|rotate=").append(config.dualPageRotateToFit)
    append("|rotateInvert=").append(config.dualPageRotateToFitInvert)
    append("|split=").append(config.dualPageSplit)
    append("|invert=").append(config.dualPageInvert)
    viewerFlags.forEach { (name, value) -> append('|').append(name).append('=').append(value) }
}
