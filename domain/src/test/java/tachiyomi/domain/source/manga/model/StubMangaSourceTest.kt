package tachiyomi.domain.source.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class StubMangaSourceTest {

    @Test
    fun `toString renders name and language when both are known`() {
        StubMangaSource(id = 1L, lang = "en", name = "AllManga")
            .toString() shouldBe "AllManga (EN)"
    }

    @Test
    fun `toString keeps the name when only the language is missing`() {
        StubMangaSource(id = 8456322126210259639L, lang = "", name = "AnimeSogo")
            .toString() shouldBe "AnimeSogo ()"
    }

    @Test
    fun `toString keeps the name when the language is only whitespace`() {
        StubMangaSource(id = 1L, lang = "   ", name = "AllManga")
            .toString() shouldBe "AllManga (   )"
    }

    @Test
    fun `toString falls back to the id when the name is unknown`() {
        StubMangaSource(id = 42L, lang = "en", name = "").toString() shouldBe "42"
    }

    @Test
    fun `toString falls back to the id for a freshly stubbed unknown source`() {
        StubMangaSource(id = 42L, lang = "", name = "").toString() shouldBe "42"
    }
}
