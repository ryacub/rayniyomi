package eu.kanade.tachiyomi.data.translation

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.util.Locale

class TargetLanguagesTest {

    @Test
    fun `default target language is English`() {
        TargetLanguages.DEFAULT shouldBe "en"
    }

    @Test
    fun `entries include English and Italian`() {
        val codes = TargetLanguages.entries("en").keys.toList()
        codes shouldContainExactly listOf(
            "en",
            "it",
            "es",
            "fr",
            "de",
            "pt-BR",
            "ru",
            "ja",
            "ko",
            "zh-CN",
        )
    }

    @Test
    fun `every fixed code is a valid BCP-47 tag`() {
        TargetLanguages.supported.forEach { code ->
            Locale.forLanguageTag(code).toLanguageTag() shouldBe code
        }
    }

    @Test
    fun `display names are readable and not raw codes`() {
        TargetLanguages.entries("en").forEach { (code, name) ->
            name.isNotBlank() shouldBe true
            name shouldNotBe code
        }
    }

    @Test
    fun `supported value appears exactly once with no duplicate entry`() {
        val entries = TargetLanguages.entries("pt-BR")
        entries.keys.count { it == "pt-BR" } shouldBe 1
    }

    @Test
    fun `custom unsupported value is appended exactly once`() {
        val entries = TargetLanguages.entries("xx-YY")
        entries.keys.filter { it == "xx-YY" } shouldHaveSize 1
        entries.keys.last() shouldBe "xx-YY"
    }

    @Test
    fun `prompt name is the English language name, not the raw tag`() {
        TargetLanguages.promptName("en") shouldBe "English"
        TargetLanguages.promptName("it") shouldBe "Italian"
        TargetLanguages.promptName("pt-BR") shouldBe "Portuguese (Brazil)"
    }

    @Test
    fun `prompt name resolves legacy Chinese tags to their script name`() {
        TargetLanguages.promptName("zh-CN") shouldBe "Chinese (Simplified)"
    }

    @Test
    fun `prompt name falls back to the code when the tag is malformed`() {
        TargetLanguages.promptName("!!!") shouldBe "!!!"
    }

    @Test
    fun `prompt name for a well-formed but unknown tag is never blank`() {
        TargetLanguages.promptName("xx-YY").isNotBlank() shouldBe true
    }

    @Test
    fun `every fixed code has an English prompt name`() {
        TargetLanguages.supported.forEach { code ->
            TargetLanguages.promptName(code) shouldNotBe code
        }
    }

    @Test
    fun `blank current code adds no extra entry`() {
        val base = TargetLanguages.entries("en")
        TargetLanguages.entries("").keys shouldContainExactly base.keys
    }
}
