package eu.kanade.tachiyomi.extension.anime.util

import eu.kanade.tachiyomi.animesource.AnimeSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class AnimeExtensionLoaderTest {

    @Test
    fun `findInvalidSource returns the first source whose id accessor throws`() {
        val good = mockk<AnimeSource>()
        val bad = mockk<AnimeSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"
        every { bad.id } throws IllegalStateException("boom")
        every { bad.lang } returns "en"
        every { bad.name } returns "bad"

        AnimeExtensionLoader.findInvalidSource(listOf(good, bad)) shouldBe bad
    }

    @Test
    fun `findInvalidSource returns the first source whose lang accessor throws`() {
        val good = mockk<AnimeSource>()
        val bad = mockk<AnimeSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"
        every { bad.id } returns 2L
        every { bad.lang } throws IllegalStateException("boom")
        every { bad.name } returns "bad"

        AnimeExtensionLoader.findInvalidSource(listOf(good, bad)) shouldBe bad
    }

    @Test
    fun `findInvalidSource returns the first source whose name accessor throws`() {
        val good = mockk<AnimeSource>()
        val bad = mockk<AnimeSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"
        every { bad.id } returns 2L
        every { bad.lang } returns "en"
        every { bad.name } throws IllegalStateException("boom")

        AnimeExtensionLoader.findInvalidSource(listOf(good, bad)) shouldBe bad
    }

    @Test
    fun `findInvalidSource returns null when all source metadata is readable`() {
        val good = mockk<AnimeSource>()

        every { good.id } returns 1L
        every { good.lang } returns "en"
        every { good.name } returns "good"

        AnimeExtensionLoader.findInvalidSource(listOf(good)) shouldBe null
    }

    @Test
    fun `findInvalidSource returns null for an empty source list`() {
        AnimeExtensionLoader.findInvalidSource(emptyList()) shouldBe null
    }

    @Test
    fun `findInvalidSource returns first invalid source when multiple sources are invalid`() {
        val first = mockk<AnimeSource>()
        val second = mockk<AnimeSource>()

        every { first.id } throws IllegalStateException("first")
        every { first.lang } returns "en"
        every { first.name } returns "first"
        every { second.id } throws IllegalStateException("second")
        every { second.lang } returns "en"
        every { second.name } returns "second"

        AnimeExtensionLoader.findInvalidSource(listOf(first, second)) shouldBe first
    }
}
