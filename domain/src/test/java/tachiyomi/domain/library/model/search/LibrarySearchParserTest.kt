package tachiyomi.domain.library.model.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class LibrarySearchParserTest {

    @Test
    fun `AND binds tighter than OR`() {
        QueryNode.from("a || b && c") shouldBe
            OrNode(listOf(GeneralQueryNode("a", false), AndNode(listOf(
                GeneralQueryNode("b", false),
                GeneralQueryNode("c", false),
            ))))
        QueryNode.from("a && b || c") shouldBe
            OrNode(listOf(AndNode(listOf(
                GeneralQueryNode("a", false),
                GeneralQueryNode("b", false),
            )), GeneralQueryNode("c", false)))
    }

    @Test
    fun `parentheses group expressions`() {
        QueryNode.from("(a || b) && c") shouldBe
            AndNode(listOf(
                OrNode(listOf(GeneralQueryNode("a", false), GeneralQueryNode("b", false))),
                GeneralQueryNode("c", false),
            ))
        QueryNode.from("((a))") shouldBe GeneralQueryNode("a", false)
    }

    @Test
    fun `leading minus negates a term`() {
        // Upstream applies the minus as a negated flag on the leaf node, not as a NotNode;
        // NotNode wraps only parenthesized groups.
        QueryNode.from("-a") shouldBe GeneralQueryNode("a", true)
        QueryNode.from("--a") shouldBe GeneralQueryNode("a", false)
        QueryNode.from("-genre:comedy") shouldBe FieldQueryNode(MangaField.GENRE, "comedy", true)
        QueryNode.from("-(a || b)") shouldBe NotNode(
            OrNode(listOf(GeneralQueryNode("a", false), GeneralQueryNode("b", false))),
        )
    }

    @Test
    fun `known field prefixes become field nodes`() {
        QueryNode.from("genre:action") shouldBe FieldQueryNode(MangaField.GENRE, "action", false)
        QueryNode.from("tag:action") shouldBe FieldQueryNode(MangaField.GENRE, "action", false)
        QueryNode.from("desc:foo") shouldBe FieldQueryNode(MangaField.DESCRIPTION, "foo", false)
        QueryNode.from("title:\"one piece\"") shouldBe FieldQueryNode(MangaField.TITLE, "one piece", false)
    }

    @Test
    fun `unknown field prefix stays a general term`() {
        QueryNode.from("foo:bar") shouldBe GeneralQueryNode("foo:bar", false)
    }

    @Test
    fun `quoted general terms keep their value`() {
        QueryNode.from("\"one piece\"") shouldBe GeneralQueryNode("one piece", false)
    }

    @Test
    fun `consecutive general terms join with implicit AND`() {
        QueryNode.from("a b") shouldBe AndNode(listOf(GeneralQueryNode("a", false), GeneralQueryNode("b", false)))
    }

    @Test
    fun `chained operators flatten into one node`() {
        QueryNode.from("a && b && c") shouldBe AndNode(listOf(
            GeneralQueryNode("a", false),
            GeneralQueryNode("b", false),
            GeneralQueryNode("c", false),
        ))
        QueryNode.from("a || b || c") shouldBe OrNode(listOf(
            GeneralQueryNode("a", false),
            GeneralQueryNode("b", false),
            GeneralQueryNode("c", false),
        ))
    }

    @Test
    fun `empty input produces an empty AND node`() {
        QueryNode.from("") shouldBe AndNode(emptyList())
        QueryNode.from("   ") shouldBe AndNode(emptyList())
    }

    @Test
    fun `malformed expressions never throw and always produce a node`() {
        assertDoesNotThrow {
            QueryNode.from("((((") shouldBe EmptyQueryNode
            QueryNode.from(")") shouldBe EmptyQueryNode
            QueryNode.from("&&") shouldBe EmptyQueryNode
            QueryNode.from("a &&") shouldBe GeneralQueryNode("a", false)
            QueryNode.from("\"unclosed") shouldBe GeneralQueryNode("\"unclosed", false)
            QueryNode.from("genre:") shouldBe GeneralQueryNode("genre:", false)
        }
    }

    @Test
    fun `dangling operators produce nodes that include empty node children`() {
        assertDoesNotThrow {
            QueryNode.from("a ||") shouldBe
                OrNode(listOf(GeneralQueryNode("a", false), EmptyQueryNode))
            QueryNode.from("(a ||") shouldBe
                OrNode(listOf(GeneralQueryNode("a", false), EmptyQueryNode))
            QueryNode.from("||") shouldBe
                OrNode(listOf(EmptyQueryNode, EmptyQueryNode))
        }
    }
}