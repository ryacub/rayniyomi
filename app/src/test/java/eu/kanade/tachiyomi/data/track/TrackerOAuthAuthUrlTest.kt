package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.simkl.SimklApi
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

class TrackerOAuthAuthUrlTest {

    @Test
    fun `anilist auth url includes state`() {
        val url = AnilistApi.authUrlString("state-value").toHttpUrl()

        url.queryParameter("state") shouldBe "state-value"
    }

    @Test
    fun `myanimelist auth url includes state and preserves pkce`() {
        val url = MyAnimeListApi.authUrlString("state-value").toHttpUrl()

        url.queryParameter("state") shouldBe "state-value"
        url.queryParameter("code_challenge").isNullOrBlank() shouldBe false
    }

    @Test
    fun `bangumi auth url includes state`() {
        val url = BangumiApi.authUrlString("state-value").toHttpUrl()

        url.queryParameter("state") shouldBe "state-value"
    }

    @Test
    fun `shikimori auth url includes state`() {
        val url = ShikimoriApi.authUrlString("state-value").toHttpUrl()

        url.queryParameter("state") shouldBe "state-value"
    }

    @Test
    fun `simkl auth url includes state`() {
        val url = SimklApi.authUrlString("state-value").toHttpUrl()

        url.queryParameter("state") shouldBe "state-value"
    }
}
