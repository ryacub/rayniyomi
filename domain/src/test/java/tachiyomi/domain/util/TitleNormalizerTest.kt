package tachiyomi.domain.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class TitleNormalizerTest {

    @Test
    fun `normalize returns lowercase title`() {
        TitleNormalizer.normalize("Attack on Titan") shouldBe "attack on titan"
    }

    @Test
    fun `normalize strips leading 'The '`() {
        TitleNormalizer.normalize("The One Piece") shouldBe "one piece"
    }

    @Test
    fun `normalize strips leading 'A '`() {
        TitleNormalizer.normalize("A Certain Magical Index") shouldBe "certain magical index"
    }

    @Test
    fun `normalize strips leading 'An '`() {
        TitleNormalizer.normalize("An Unlikely Hero") shouldBe "unlikely hero"
    }

    @Test
    fun `normalize removes punctuation`() {
        TitleNormalizer.normalize("One Piece!") shouldBe "one piece"
    }

    @Test
    fun `normalize replaces hyphens with space`() {
        TitleNormalizer.normalize("Sword Art Online: Alicization") shouldBe "sword art online alicization"
    }

    @Test
    fun `normalize collapses multiple spaces`() {
        TitleNormalizer.normalize("My  Hero  Academia") shouldBe "my hero academia"
    }

    @Test
    fun `normalize trims whitespace`() {
        TitleNormalizer.normalize("  Naruto  ") shouldBe "naruto"
    }

    @Test
    fun `normalize handles empty string`() {
        TitleNormalizer.normalize("") shouldBe ""
    }

    @Test
    fun `normalize handles all-punctuation string`() {
        TitleNormalizer.normalize("!!!") shouldBe ""
    }

    @Test
    fun `normalize is idempotent`() {
        val first = TitleNormalizer.normalize("The One Piece!")
        val second = TitleNormalizer.normalize(first)
        first shouldBe second
    }

    @Test
    fun `normalize does not strip 'The' mid-title`() {
        TitleNormalizer.normalize("Beyond the Sky") shouldBe "beyond the sky"
    }

    @Test
    fun `normalize handles unicode fullwidth characters`() {
        TitleNormalizer.normalize("Ｏｎｅ Ｐｉｅｃｅ") shouldBe "ｏｎｅ ｐｉｅｃｅ"
    }
}
