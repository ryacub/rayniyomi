package eu.kanade.tachiyomi.data.translation.catalog

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class JsonObjectsTest {

    @Test
    fun `string returns the value for an existing key`() {
        val obj = catalogJson.parseToJsonElement("""{"name":"claude-sonnet-4-5"}""").jsonObject

        obj.string("name") shouldBe "claude-sonnet-4-5"
    }

    @Test
    fun `string returns null for a missing key`() {
        val obj = catalogJson.parseToJsonElement("""{"name":"claude-sonnet-4-5"}""").jsonObject

        obj.string("other") shouldBe null
    }

    @Test
    fun `textArray returns the strings`() {
        val obj = catalogJson.parseToJsonElement("""{"tags":["text","image"]}""").jsonObject

        obj.textArray("tags") shouldBe listOf("text", "image")
    }

    @Test
    fun `textArray skips non primitive elements and keeps the rest`() {
        val obj = catalogJson.parseToJsonElement("""{"tags":["text",{"nested":true},"image"]}""").jsonObject

        obj.textArray("tags") shouldBe listOf("text", "image")
    }

    @Test
    fun `catalogRootObject throws with provider label for non object body`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            catalogRootObject("""[{"id":"x"}]""", "Claude")
        }

        error.message shouldBe "Claude catalog response is not a JSON object"
    }
}
