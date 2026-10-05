package eu.kanade.tachiyomi.ui.webview

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource

/** The WebView screen for the source home page, or null when the source has no website. */
fun sourceWebViewScreen(source: MangaSource): WebViewScreen? {
    val httpSource = source as? HttpSource ?: return null
    return WebViewScreen(url = httpSource.getHomeUrl(), initialTitle = httpSource.name, sourceId = httpSource.id)
}

/** The WebView screen for the source home page, or null when the source has no website. */
fun sourceWebViewScreen(source: AnimeSource): WebViewScreen? {
    val httpSource = source as? AnimeHttpSource ?: return null
    return WebViewScreen(url = httpSource.baseUrl, initialTitle = httpSource.name, sourceId = httpSource.id)
}
