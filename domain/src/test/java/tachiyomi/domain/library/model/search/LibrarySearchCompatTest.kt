package tachiyomi.domain.library.model.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class LibrarySearchCompatTest {

    @Test
    fun `legacy queries route to the legacy matcher`() {
        listOf(
            "naruto",
            "naruto shipuden",
            "manga, action",
            "-x",
            "-x,y",
            "id:5",
            "ID:5",
            "id:5,foo",
            "Re:Zero",
            "http://example.com",
            "水星の魔女",
            "Gintama'",
            "(G)I-DLE",
            "Sousou no Frieren (TV)",
            "Knockin' on Heaven's Door",
            "xgenre:action",
        ).forEach { query ->
            isLegacySearchQuery(query) shouldBe true
        }
    }

    @Test
    fun `new grammar queries route to the parsed matcher`() {
        listOf(
            "a && b",
            "a || b",
            "(a || b)",
            "\"one piece\"",
            "title:op",
            "TITLE:op",
            "naruto genre:action",
            "genre:action,adventure",
            "desc:foo || genre:bar",
            "-genre:comedy",
            "notes:foo",
            "lang:en",
            "unread>5",
            "id>5",
            "UNREAD>5",
            "ID>5",
            "added>=2024-01-01",
            "fi=7",
        ).forEach { query ->
            isLegacySearchQuery(query) shouldBe false
        }
    }

    @Test
    fun `plain text and unknown comparison syntax stay legacy`() {
        listOf(
            "title>5",
            "unreadable>1",
        ).forEach { query ->
            isLegacySearchQuery(query) shouldBe true
        }
    }

    @Test
    fun `id prefix always routes legacy even when uppercase`() {
        // Preserved quirk: "ID:" matches the startsWith check case-insensitively, so it routes
        // legacy, but the legacy substringAfter("id:") lookup is case-sensitive and never finds
        // the prefix, so "ID:<n>" never matches. This mirrors the pre-ticket behavior exactly.
        isLegacySearchQuery("ID:5") shouldBe true
    }

    @Test
    fun `parseSearchQuery returns the parsed tree for valid input`() {
        parseSearchQuery("a && b") shouldBe AndNode(
            listOf(
                GeneralQueryNode("a", false),
                GeneralQueryNode("b", false),
            ),
        )
        parseSearchQuery("") shouldBe AndNode(emptyList())
    }

    @Test
    fun `parseSearchQuery never throws`() {
        assertDoesNotThrow {
            (parseSearchQuery("!!garbage(") is QueryNode) shouldBe true
            (parseSearchQuery("((((") is QueryNode) shouldBe true
        }
    }
}
