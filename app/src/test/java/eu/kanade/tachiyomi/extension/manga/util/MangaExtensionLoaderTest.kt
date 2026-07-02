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
    fun `findInvalidSource returns null when all source metadata is readable`() {
        val good = mockk<MangaSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"

        MangaExtensionLoader.findInvalidSource(listOf(good)) shouldBe null
    }

    @Test
    fun `findInvalidSource returns null for an empty source list`() {
        MangaExtensionLoader.findInvalidSource(emptyList()) shouldBe null
    }

    @Test
    fun `findInvalidSource returns first invalid source when multiple sources are invalid`() {
        val first = mockk<MangaSource>()
        val second = mockk<MangaSource>()

        every { first.id } throws IllegalStateException("first")
        every { first.lang } returns "en"
        every { first.name } returns "first"
        every { second.id } throws IllegalStateException("second")
        every { second.lang } returns "en"
        every { second.name } returns "second"

        MangaExtensionLoader.findInvalidSource(listOf(first, second)) shouldBe first
    }
}
