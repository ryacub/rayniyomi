package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.TvType

class TestApi(
    override val hasMainPage: Boolean = true,
    override val mainPage: List<MainPageData> = listOf(MainPageData("Latest", "https://site.test/latest")),
) : MainAPI() {
    override var name = "Test Api"
    override var mainUrl = "https://site.test"
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Anime)
}
