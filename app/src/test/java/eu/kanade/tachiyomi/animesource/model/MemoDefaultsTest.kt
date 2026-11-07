package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class MemoDefaultsTest {

    private val memo = JsonObject(mapOf("token" to JsonPrimitive("kept")))

    @Test
    fun `constructors without memo leave an empty memo`() {
        Hoster(hosterUrl = "https://example.com/h", hosterName = "Host").memo shouldBe JsonObject(emptyMap())
        Video(videoUrl = "https://example.com/v", videoTitle = "Video").memo shouldBe JsonObject(emptyMap())
    }

    @Test
    fun `copy keeps the memo of the source`() {
        Hoster(hosterName = "Host", memo = memo).copy(lazy = true).memo shouldBe memo
        Video(videoUrl = "https://example.com/v", memo = memo).copy(videoTitle = "V").memo shouldBe memo
    }
}
