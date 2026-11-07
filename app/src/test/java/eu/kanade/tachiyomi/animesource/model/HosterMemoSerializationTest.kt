package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class HosterMemoSerializationTest {

    private val storedHosters = """
        [{"hosterUrl":"https://example.com/h","hosterName":"Host",
          "videoList":"[{\"videoUrl\":\"https://example.com/v\",\"videoTitle\":\"Video\",\"memo\":{\"token\":\"video-token\"}}]",
          "internalData":"","lazy":false,"memo":{"token":"host-token"}}]
    """.trimIndent()

    @Test
    fun `hoster memo survives serialize and toHosterList`() {
        val encoded = runCatching { storedHosters.toHosterList().serialize() }.getOrNull()
            .shouldNotBeNull()

        Json.parseToJsonElement(encoded).jsonArray[0].jsonObject["memo"]!!
            .jsonObject["token"]!!.jsonPrimitive.content shouldBe "host-token"
    }

    @Test
    fun `video memo survives serialize and toHosterList`() {
        val encoded = runCatching { storedHosters.toHosterList().serialize() }.getOrNull()
            .shouldNotBeNull()

        val videoList = Json.parseToJsonElement(encoded).jsonArray[0].jsonObject["videoList"]!!
            .jsonPrimitive.content
        Json.parseToJsonElement(videoList).jsonArray[0].jsonObject["memo"]!!
            .jsonObject["token"]!!.jsonPrimitive.content shouldBe "video-token"
    }
}
