package tachiyomi.domain.library.model.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.And
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.Field
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.General
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.LParen
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.Not
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.Or
import tachiyomi.domain.library.model.search.LibrarySearchLexer.Token.RParen

class LibrarySearchLexerTest {

    @Test
    fun `tokenizes AND operator`() {
        LibrarySearchLexer.tokenize("a && b") shouldBe listOf(General("a"), And, General("b"))
    }

    @Test
    fun `tokenizes OR operator`() {
        LibrarySearchLexer.tokenize("a || b") shouldBe listOf(General("a"), Or, General("b"))
    }

    @Test
    fun `tokenizes parentheses`() {
        LibrarySearchLexer.tokenize("(a || b) && c") shouldBe
            listOf(LParen, General("a"), Or, General("b"), RParen, And, General("c"))
    }

    @Test
    fun `tokenizes leading minus as NOT only when glued to the term`() {
        LibrarySearchLexer.tokenize("-naruto") shouldBe listOf(Not, General("naruto"))
        LibrarySearchLexer.tokenize("- naruto") shouldBe listOf(General("-"), General("naruto"))
        LibrarySearchLexer.tokenize("-,") shouldBe listOf(General("-"))
        LibrarySearchLexer.tokenize("--x") shouldBe listOf(Not, Not, General("x"))
    }

    @Test
    fun `tokenizes field value pairs`() {
        LibrarySearchLexer.tokenize("foo:bar") shouldBe listOf(Field("foo", "bar"))
        LibrarySearchLexer.tokenize("-genre:comedy") shouldBe listOf(Not, Field("genre", "comedy"))
    }

    @Test
    fun `tokenizes quoted field values`() {
        LibrarySearchLexer.tokenize("title:\"one piece\"") shouldBe listOf(Field("title", "one piece"))
    }

    @Test
    fun `tokenizes quoted general terms`() {
        LibrarySearchLexer.tokenize("\"one piece\"") shouldBe listOf(General("one piece"))
    }

    @Test
    fun `tokenizes unicode`() {
        LibrarySearchLexer.tokenize("水星の魔女") shouldBe listOf(General("水星の魔女"))
        LibrarySearchLexer.tokenize("\"水星\"") shouldBe listOf(General("水星"))
        LibrarySearchLexer.tokenize("title:水星") shouldBe listOf(Field("title", "水星"))
    }

    @Test
    fun `comma is a separator between terms`() {
        LibrarySearchLexer.tokenize("genre:action,adventure") shouldBe
            listOf(Field("genre", "action"), General("adventure"))
        LibrarySearchLexer.tokenize("manga, action") shouldBe listOf(General("manga"), General("action"))
    }

    @Test
    fun `id equals syntax has no comparison token and becomes a general term`() {
        LibrarySearchLexer.tokenize("id=5") shouldBe listOf(General("id=5"))
    }

    @Test
    fun `single quote is not a quote character`() {
        LibrarySearchLexer.tokenize("Gintama'") shouldBe listOf(General("Gintama'"))
    }

    @Test
    fun `space separated terms become separate generals`() {
        LibrarySearchLexer.tokenize("naruto shipuden") shouldBe
            listOf(General("naruto"), General("shipuden"))
    }

    @Test
    fun `empty input produces no tokens`() {
        LibrarySearchLexer.tokenize("") shouldBe emptyList()
        LibrarySearchLexer.tokenize("   ") shouldBe emptyList()
    }
}
