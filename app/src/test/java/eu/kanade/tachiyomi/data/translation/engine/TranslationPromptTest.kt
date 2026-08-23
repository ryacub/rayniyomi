package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.data.translation.TargetLanguages
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class TranslationPromptTest {

    @Test
    fun `the prompt names the language in English, not by its tag`() {
        val prompt = TranslationPrompt.build("pt-BR")

        prompt shouldContain "translated to Portuguese (Brazil)"
        prompt shouldNotContain "translated to pt-BR"
    }

    @Test
    fun `a legacy Chinese tag reaches the prompt as a script name`() {
        TranslationPrompt.build("zh-CN") shouldContain "translated to Chinese (Simplified)"
    }

    @Test
    fun `no fixed language reaches the prompt as a raw tag`() {
        TargetLanguages.supported.forEach { code ->
            TranslationPrompt.build(code) shouldNotContain "translated to $code"
        }
    }

    @Test
    fun `a custom value still reaches the prompt`() {
        TranslationPrompt.build("!!!") shouldContain "translated to !!!"
    }

    @Test
    fun `the prompt still asks for a bare JSON array`() {
        val prompt = TranslationPrompt.build(TargetLanguages.DEFAULT)

        prompt shouldContain "Return ONLY a JSON array"
        prompt.contains("\"translated\"") shouldBe true
    }
}
