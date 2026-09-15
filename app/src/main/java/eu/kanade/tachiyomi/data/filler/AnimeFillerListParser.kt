package eu.kanade.tachiyomi.data.filler

import org.jsoup.Jsoup
import tachiyomi.domain.util.TitleNormalizer

internal data class AnimeFillerCatalogEntry(
    val title: String,
    val path: String,
)

internal data class AnimeFillerDetail(
    val title: String,
    val fillerEpisodes: Set<Double>,
)

internal object AnimeFillerListParser {

    fun parseCatalog(html: String): List<AnimeFillerCatalogEntry>? {
        val showList = Jsoup.parse(html).selectFirst("#ShowList") ?: return null
        return showList.select("a[href^=/shows/]")
            .mapNotNull { link ->
                val title = link.text().trim()
                val path = link.attr("href").trim()
                if (title.isEmpty() || path.isEmpty()) {
                    null
                } else {
                    AnimeFillerCatalogEntry(title, path)
                }
            }
    }

    fun parseDetail(html: String): AnimeFillerDetail? {
        val document = Jsoup.parse(html)
        val title = document.select("span[property=dc:title]").first()?.attr("content")?.trim()
            ?: return null
        val episodes = document.select("table.EpisodeList tr").mapNotNull { row ->
            val classes = row.classNames()
            if ("filler" !in classes || "mixed_canon/filler" in classes) return@mapNotNull null
            row.selectFirst("td.Number")?.text()?.trim()?.toDoubleOrNull()
        }.toSet()
        return AnimeFillerDetail(title, episodes)
    }

    fun normalizeTitle(title: String): String = TitleNormalizer.normalize(title)
}
