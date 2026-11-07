package xyz.rayniyomi.cloudstream.host

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AniListApiNestedTypesTest {

    @Test
    fun `nested AniList types that Anichi v29 references resolve by name`() {
        val names = listOf(
            "Title",
            "CoverImage",
            "LikePageInfo",
            "RecommendationConnection",
            "SeasonNextAiringEpisode",
        )

        names.map { simpleName ->
            Class.forName("com.lagradost.cloudstream3.syncproviders.providers.AniListApi\$$simpleName").simpleName
        } shouldBe names
    }
}
