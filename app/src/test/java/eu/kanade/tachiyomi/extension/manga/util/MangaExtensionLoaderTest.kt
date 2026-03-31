package eu.kanade.tachiyomi.extension.manga.util

import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class MangaExtensionLoaderTest {

    @Test
    fun `findInvalidSource returns the first source whose id accessor throws`() {
        val good = mockk<MangaSource>()
        val bad = mockk<MangaSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"
        every { bad.id } throws IllegalStateException("boom")
        every { bad.lang } returns "en"
        every { bad.name } returns "bad"

        MangaExtensionLoader.findInvalidSource(listOf(good, bad)) shouldBe bad
    }

    @Test
    fun `findInvalidSource returns the first source whose lang accessor throws`() {
        val good = mockk<MangaSource>()
        val bad = mockk<MangaSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"
        every { bad.id } returns 2L
        every { bad.lang } throws IllegalStateException("boom")
        every { bad.name } returns "bad"

        MangaExtensionLoader.findInvalidSource(listOf(good, bad)) shouldBe bad
    }

    @Test
    fun `findInvalidSource returns the first source whose name accessor throws`() {
        val good = mockk<MangaSource>()
        val bad = mockk<MangaSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"
        every { bad.id } returns 2L
        every { bad.lang } returns "en"
        every { bad.name } throws IllegalStateException("boom")

        MangaExtensionLoader.findInvalidSource(listOf(good, bad)) shouldBe bad
    }

    @Test
    fun `findInvalidSource returns null when all source ids are readable`() {
        val good = mockk<MangaSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"

        MangaExtensionLoader.findInvalidSource(listOf(good)) shouldBe null
    }
}
